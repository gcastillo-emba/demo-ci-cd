# Plan maestro — POC "Asuntos & Horas" (MADA-MB)

## Context

El repo `gcastillo-emba/demo-ci-cd` está vacío: solo `README.md` y un commit. Hay que construir, de punta a punta y con agentes, un POC de registro de horas para una firma legal, con datos 100 % sintéticos, CI/CD completo y metodología MADA-MB (spec-driven con OpenSpec, gates mecánicos y de juicio, bitácora de fallas). En el camino se ejercitan y documentan las capacidades de Claude Code. Este documento es el entregable de F0. Se diseñó con un panel de 3 planificadores de solo lectura (arquitectura, CI/CD y seguridad, capacidades de Claude Code) más un crítico de completitud. Sus contradicciones ya están resueltas abajo.

## F0 — Entorno verificado (2026-11-07)

- java 21.0.12, Maven 3.9.16, node 22.23.2, npm 12.0.2 (sin pnpm), Docker 29.6.2 + Compose v5.3.1 (motor Linux, 12 CPU, 8 GB).
- gh 2.99.0 (ADMIN), openspec 1.12.0, Claude Code 2.1.241, git 2.55.
- Decisiones del usuario:
  - **El repo se hace público.** Habilita gratis el ruleset, CodeQL, dependency review, secret scanning con push protection y minutos ilimitados.
  - **Sin `ANTHROPIC_API_KEY`.** `claude-review.yml` y `evals-real.yml` quedan listos e inertes (guard de secreto más `vars.CLAUDE_REVIEW_ENABLED`). El script `claude -p` corre en local.
- Hallazgos de OpenSpec 1.12: los cambios se crean con `openspec new change <id>`. En CI se valida con `openspec validate --all --strict --no-interactive`.
- Riesgo Windows 1: Node 22 no puede hacer spawn de `.cmd` con `shell:false` (EINVAL), y la ruta contiene un espacio (`Sistemas IA`). Por eso todo spawn pasa por un helper `scripts/lib/run.mjs` que usa `shell:true` y cita cada argumento.
- Riesgo Windows 2: CRLF. Se agrega `.gitattributes` (`eol=lf`, `*.cmd crlf`) más `git add --renormalize .` en el primer commit.

## Decisiones técnicas clave (se fijan como ADR en F1)

- **Monorepo**: `api/openapi.yaml` (OpenAPI 3.1, única fuente) · `backend/` (Spring Boot 3.5.x, un solo módulo Maven, mvnw) · `frontend/` (Vite React TS; nginx hace proxy de `/api` → mismo origen, sin CORS) · `e2e/` (Playwright) · `evals/description-assistant/cases/*.yaml` · `openspec/` · `docs/` · `scripts/*.mjs` · `.claude/` · `.github/`.
  - El `package.json` raíz es el task runner multiplataforma: workspaces `frontend` y `e2e`; scripts `setup`, `dev`, `api:lint`, `api:generate`, `api:check`, `spec:validate`, `backend:verify`, `frontend:*`, `evals`, `e2e`, `gate`, `changelog`.
- **Hexagonal**: `com.emba.asuntoshoras.{domain, application.port.in|out, application.service, adapters.in.web, adapters.out.persistence|security|assistant.{fake,anthropic}, config}`.
  - La autorización por rol y por propiedad vive en `application`, así queda cubierta por JaCoCo.
  - ArchUnit verifica:
    - las capas onion;
    - que el dominio no dependa de nada;
    - que `@Entity` solo exista en persistence y `@RestController` solo en web;
    - que nadie llame a `*.now()` sin `Clock`;
    - que no haya inyección por campo;
    - que el SDK de Anthropic solo se use en su adaptador;
    - que cada `DomainException` tenga entrada en `ProblemCatalog`.
- **API-first**:
  - Backend: openapi-generator-maven-plugin (`spring`, `interfaceOnly`) genera las interfaces que implementan los controllers, así que cualquier drift rompe la compilación. Además, `ContractConformanceIT` exige que el conjunto de endpoints sea igual al del contrato.
  - springdoc solo sirve Swagger UI sobre el YAML estático.
  - Frontend: openapi-typescript + openapi-fetch generan `frontend/src/api/generated/schema.ts`, que se versiona. `scripts/api-client.mjs --check` compara en memoria normalizando CRLF.
  - El contrato se lintea con @redocly/cli (fijado como devDependency).
- **Datos y reglas**:
  - Base PostgreSQL 17 (`postgres:17-alpine`, la misma imagen en compose y en Testcontainers ≥ 1.21.4). Migraciones Flyway V1…V3 (users, clients/matters, time_entries).
  - Las horas se guardan como **décimas enteras** (`hours_tenths`, con CHECK 1..240).
  - "Hoy" se calcula con un `Clock` en `America/La_Paz`, configurable.
  - La regla de 24 h suma PENDING y APPROVED. Se serializa con `pg_advisory_xact_lock` por abogado y día, y lo cubre un IT de concurrencia.
  - La ventana de edición de 7 días se cuenta desde la creación.
  - APPROVED y REJECTED son estados terminales.
  - El seed de demo es un `R__` idempotente por capacidad, con fechas relativas (N ≥ 1), y solo se carga con el perfil `demo`.
- **Errores**: RFC 9457. `type=https://asuntos-horas.example/problems/{slug}`; `title` y `detail` en español; `code` estable en inglés; `errors[]` para validación; `traceId`.
  - Códigos HTTP: 400 validación · 401 · 403 rol o propiedad · 404 · 405 método no ofrecido (`method-not-allowed`) · 409 estado o concurrencia · 422 reglas de negocio · 500 error inesperado (`internal-error`).
  - Precedencia base cuando una petición incumple varias reglas: `401 > 403 > 400 > 404 > 409`. El `403` por rol se aplica con reglas por URL (antes de validar el cuerpo). Un método no ofrecido responde `405` antes de evaluar el rol. Las excepciones acordadas entre cambios están en `design.md` § Supuestos (S11) de `add-clients-matters`.
  - Errores sin detalles internos: ningún Problem Details expone traza, SQL, nombres de clase ni el mensaje original de la excepción; un error no previsto responde `500 internal-error` con `detail` genérico en español y `traceId`.
- **Seguridad**: JWT HS256 (Nimbus) con TTL de 2 h; usuarios de demo sintéticos con BCrypt; el token se guarda en sessionStorage (ADR). Los logs son ECS estructurados y el MDC solo lleva userId y requestId, nunca PII.
- **C4**:
  - El puerto es `DescriptionAssistant`. El adaptador `fake` es determinista y es el default. El adaptador `anthropic` usa `java.net.http` con timeout.
  - El flag `app.features.description-assistant` se expone en `GET /api/features`.
  - Guardrails: entrada de 1 a 500 caracteres, salida de máximo 300, nunca vacía, sin PII en logs. Los cubre un test de captura de logs.
  - 10 casos de eval se corren con `-Pevals` (fake) en CI y con dispatch manual contra el modelo real.

## CI/CD (GitHub Actions; acciones fijadas por SHA, `permissions` mínimos, `timeout-minutes`)

- `ci.yml` (PR y push a main): `changes` (paths-filter) → `spec` · `contract` (api:lint + api:check) · `backend` (`mvnw verify`: unit + ArchUnit + Testcontainers + JaCoCo ≥ 80 % en domain+application + `spotless:check`) · `frontend` (lint, `prettier --check`, typecheck, Vitest) · `evals` (fake) · `e2e` (bake con caché gha → `compose up --wait` → Playwright → logs y artefactos si falla → `down -v`) → **`ci-ok`**, un agregador que es el único check requerido.
- `security.yml`: CodeQL (java-kotlin y javascript-typescript, security-extended) + dependency-review (high) + gitleaks. Secret scanning y push protection se activan en la configuración del repo. `dependabot.yml` cubre maven, npm y actions, agrupado, semanal, e ignora majors de Spring Boot.
- `pr-title.yml`: Conventional Commits con scopes `c1|c2|c3|c4|api|spec|ci|docs|deps`. El repo solo permite squash merge.
- `cd.yml`: `workflow_run` de CI exitoso sobre main publica las imágenes `ghcr.io/gcastillo-emba/demo-ci-cd-{backend,frontend}` con tags `sha-*`, `main` y `latest`, amd64, con attestations. Los paquetes son públicos.
- `release.yml`: con tag `v*` re-etiqueta las imágenes `sha-*` (sin rebuild), genera notas con git-cliff (`cliff.toml` con secciones en español) y crea la release con `gh release create`, adjuntando `docker-compose.demo.yml` y el runbook.
- `claude-review.yml` y `evals-real.yml`: inertes sin secreto.
- **Ruleset en main**: exige PR con 0 aprobaciones (GitHub no deja aprobar un PR propio), check `ci-ok` requerido, sin force push ni borrado, historial lineal.
- **Merge del agente**: solo con `scripts/merge-si-verde.mjs`. El script exige `ci-ok` en SUCCESS y las etiquetas `revisor:aprobado` y `seguridad:ok` puestas después del último commit, y luego ejecuta `gh pr merge --squash --match-head-commit <sha>`. Esta desviación se registra en ADR-004.

## Capa Claude Code (`.claude/`)

- **CLAUDE.md** importa `@AGENTS.md`. AGENTS.md es el contrato neutral de agentes: rutas que posee cada rol, definición de terminado, formato de salida. CLAUDE.md agrega comandos, gates y convenciones.
- **Hooks** (Node puro, `node "$CLAUDE_PROJECT_DIR/.claude/hooks/x.mjs"`, con tests con fixtures):
  - `guard-secrets` (PreToolUse Edit|Write y Bash|PowerShell) bloquea `.env*`, salvo `.env.example`, además de llaves y credenciales.
  - `guard-git-push` bloquea force push, pushes a main y pushes de tags, y también `gh pr merge` fuera del script.
  - `format-on-edit` (PostToolUse) aplica Prettier vía API y Spotless con `-DspotlessFiles`. Ignora `generated/`. Si un JVM por edición resulta muy lento, Spotless pasa solo a `/gate` y CI (queda en bitácora).
  - `bitacora-on-stop` (Stop) solo avisa con `systemMessage` cuando `.mada/fallas.jsonl` tiene fallas posteriores a la última edición de la bitácora. Agrega la regla de parada de 3 fallas.
  - `session-context` (SessionStart) imprime `.mada/estado.json` para retomar tras compactar.
- **Subagentes** con herramientas mínimas:
  - arquitecto: Read/Grep/Glob/Write/Edit, limitado a docs/ADR/design.
  - redactor-specs: además Bash para openspec, limitado a openspec/ y api/.
  - dev-backend: limitado a backend/.
  - dev-frontend: limitado a frontend/, excepto generated/.
  - qa: tests, e2e, evals y Playwright MCP.
  - seguridad: solo lectura + `gh pr comment`.
  - devops: limitado a .github/, Docker y scripts.
  - revisor: solo lectura + `gh`; nunca revisa trabajo propio.
  - `scripts/check-ownership.mjs` verifica las rutas de forma mecánica.
- **Skills**: `nueva-capacidad`, con el procedimiento MADA completo y los comandos openspec verificados, y `adr`, con plantilla y numeración automática. Se escriben al terminar C1 y se reutilizan en C2–C4.
- **Comandos**: `/gate [--rapido|--e2e]` (llama a `scripts/gate.mjs`, que registra tiempos en `.mada/`), `/merge-si-verde`, `/bitacora`, `/evidencia`, `/revisar-pr`.
- **Otros**:
  - `.mcp.json` con Playwright MCP y `.claude/launch.json` para el Browser pane.
  - Headless: `scripts/claude-resumen-bitacora.mjs` (`claude -p`) resume la bitácora y redacta el borrador del informe; corre manual o por workflow_dispatch.
- **Orquestación** (ultracode): cada capacidad de F4 corre un Workflow.
  - Primero, el contrato más el cliente generado se commitean en `feat/cN`.
  - Luego dev-backend y dev-frontend trabajan en paralelo, cada uno en un **worktree aislado**.
  - Se verifica la propiedad de rutas y se hace merge `--no-ff` en `feat/cN`.
  - qa escribe E2E y evidencia; `/gate --e2e` corre en segundo plano.
  - Revisan revisor y seguridad, con verificación adversarial de los hallazgos.
  - Se corrige y se hace merge.
  - Las verificaciones pesadas (Testcontainers, compose) se serializan porque Docker tiene 8 GB.
  - La lista de tareas visible espeja `tasks.md`.

## Fases y PRs (un PR por cambio; squash; el agente fusiona si está verde y aprobado)

1. **F0/F1 — PR `chore: encuadre y contexto`**
   - Higiene: `.gitattributes`, `.gitignore`, `.editorconfig`, `.nvmrc`, `package.json` raíz, `scripts/lib/run.mjs`.
   - Estructura y `openspec init`.
   - Capa `.claude/` completa, con pruebas de humo de hooks y de aislamiento por worktree.
   - Documentos: `docs/plan-maestro.md` (este plan), `glosario.md`, `bitacora-fallas.md`, ADR-001 (hexagonal + API-first), ADR-002 (pruebas y arnés), ADR-003 (CI/CD), ADR-004 (auto-merge del agente), ADR-005 (repo público y modo de seguridad), ADR-006 (autenticación JWT HS256, adelantado desde F3), y C4 nivel 1 y 2 en Mermaid.
   - CI mínimo (`spec` + gitleaks + `ci-ok`).
   - Antes del push: escaneo de secretos y cambio de visibilidad a público vía `gh`.
2. **F2 — PR `docs(spec): propuestas C1–C4`** (excepción documentada: las 4 specs en un solo PR; cada PR de F4 implementa y archiva su cambio)
   - Cambios: `add-clients-matters`, `add-time-entries`, `add-hours-dashboard`, `add-description-assistant`.
   - Cada uno trae `proposal.md`, requisitos DEBE/NO DEBE, escenarios Dado/Cuando/Entonces, `design.md` con sección "Supuestos" y `tasks.md` dividido en contrato, backend, frontend y qa.
   - Pasan `validate --strict`. El revisor aplica un checklist de specs (trazabilidad con el brief) y deja la evidencia en el PR.
3. **F3 — PR `feat: walking skeleton`**
   - Contrato: login, `/me`, `/features`, `Problem`, `Page`.
   - Backend: V1 users, JWT, ProblemDetails, Clock, ArchUnit, un IT con Testcontainers, JaCoCo, Spotless, Actuator, Dockerfile.
   - Frontend: login con el cliente generado.
   - Compose con cadena de healthchecks (Actuator se expone bajo `/api/actuator`) y E2E de login.
   - CI completo, security, pr-title, dependabot, cd, release, claude-review inerte.
   - Ruleset en main y paquetes públicos.
   - **No se empieza C1 sin `ci-ok` verde e imágenes en GHCR.**
4. **F4 — PRs `feat(c1)` → `feat(c2)` → `feat(c3)` → `feat(c4)`**, con el ciclo de orquestación descrito arriba.
   - C2 es el riesgo principal: tests de borde con `Clock.fixed`, IT de concurrencia, un slug por regla, UI de aprobar y rechazar con motivo obligatorio, E2E de edición y del límite diario.
   - C3: tablas accesibles con barras CSS y ventana fija de 30 días; un test de alcance por rol.
   - C4 mínimo viable: flag, puerto, fake, guardrails, 10 evals y UI de aceptar/editar/descartar con flag apagado probado. El adaptador Anthropic tiene un test unitario con HTTP simulado.
5. **F5 — PR `docs: runbook y release`**
   - Se agregan `docker-compose.demo.yml` (pull de GHCR) y `docs/runbook.md`, y se cronometra el arranque.
   - El agente prepara el tag `v0.1.0` en local y **pide confirmación**. El hook bloquea pushes de tags del agente, así que el push lo hace el humano y ese push dispara `release.yml`.
6. **F6 — PR `docs: retro y evidencia`**: `docs/informe-demo.md` (matriz capacidad → dónde → funcionó/falló → recomendación, con enlaces de evidencia), `docs/metricas-demo.md` (generado desde `gh pr list`, `gh run list` y `.mada/`), bitácora final, `docs/propuesta-mejora-mada.md`, y README con dos rutas: pull de imágenes en menos de 5 min, o build.

Al cerrar cada fase va un resumen breve. Las paradas son las del brief: dinero o nube, secreto faltante, acción irreversible externa, o el mismo gate fallando 3 veces.

## Recortes de alcance si el tiempo o el contexto se agotan

- Se elimina la validación de respuestas con swagger-request-validator; basta la compilación contra las interfaces más ContractConformanceIT.
- C3 y C4 corren de forma secuencial en un solo worktree.
- Si F3 se atrasa, CodeQL y CD pasan a después de C1.
- Prioridad: C1–C3 completas; C4 queda documentada como pendiente antes que entregar capacidades a medias.

## Verificación

- Local: `npm run gate -- --e2e` en verde en Windows, que corre spec, contrato, `mvnw verify` con JaCoCo, frontend, evals y E2E sobre compose.
- Remoto: `ci-ok` verde en cada PR; CodeQL sin alertas high; imágenes en GHCR; release creada por el tag.
- Visual: capturas con Playwright en `docs/evidence/<cambio>/`, enlazadas en cada PR, más una inspección en el Browser pane.
- Demo: `docker compose -f docker-compose.demo.yml up` levanta todo y el login funciona con las credenciales de demo del README.

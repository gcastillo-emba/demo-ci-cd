@AGENTS.md

# CLAUDE.md — Cómo trabajar en este repo con Claude Code

Este archivo complementa `AGENTS.md` (importado arriba) con lo específico de Claude Code. Fuente de verdad: `docs/plan-maestro.md`.

## Comandos

> Disponibles hoy (F1): `gate`, `spec:validate`, `hooks:test`, `scripts:test`, `format:check`, `setup`. El resto (`api:*`, `backend:verify`, `frontend:*`, `dev`, `evals`, `e2e`, `evidence`) y los workspaces `frontend`/`e2e` se agregan al `package.json` en **F3** (walking skeleton).

| Propósito                                                      | Comando                                                                                                      |
| -------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Gate local completo (rápido / con E2E)                         | `npm run gate` · `npm run gate -- --rapido` · `npm run gate -- --e2e`                                        |
| Validar specs OpenSpec                                         | `npm run spec:validate`                                                                                      |
| Generar / verificar cliente del contrato                       | `npm run api:generate` · `npm run api:check`                                                                 |
| Lint del contrato                                              | `npm run api:lint`                                                                                           |
| Backend (unit + ArchUnit + Testcontainers + JaCoCo + Spotless) | `npm run backend:verify`                                                                                     |
| Frontend                                                       | `npm run frontend:lint` · `npm run frontend:typecheck` · `npm run frontend:test` · `npm run dev -w frontend` |
| Evals del asistente (fake)                                     | `npm run evals`                                                                                              |
| E2E / evidencia                                                | `npm run e2e` · `npm run evidence -- <cambio>`                                                               |
| Stack completo                                                 | `docker compose up --build --wait` · `docker compose down -v`                                                |
| Tests de hooks / scripts                                       | `npm run hooks:test` · `npm run scripts:test`                                                                |
| Formato                                                        | `npm run format:check`                                                                                       |

## Convenciones

- Código en inglés; UI y documentación en español. Usar los términos de `docs/glosario.md`.
- Paquetes backend: `com.emba.asuntoshoras.{domain, application.port.in|out, application.service, adapters.in.web, adapters.out.persistence|security|assistant, config}`.
- El contrato `api/openapi.yaml` es la única fuente de la API; el cliente `frontend/src/api/generated/` se regenera, no se edita.
- Errores RFC 9457 con `code` estable en inglés y `title`/`detail` en español.
- ADRs en `docs/adr/ADR-NNN-titulo.md` mediante la skill `/adr` (numeración automática).
- Ramas: `feat/cN`, `docs/...`, `chore/...`. Nunca trabajar directo en `main`.

## Gates

| Tipo     | Gate                                                         | Dónde              | Quién decide          |
| -------- | ------------------------------------------------------------ | ------------------ | --------------------- |
| Mecánico | `spec` (`openspec validate --strict`)                        | CI + `/gate`       | Máquina               |
| Mecánico | `contract` (`api:lint` + `api:check`)                        | CI + `/gate`       | Máquina               |
| Mecánico | `backend` (`mvnw verify`, JaCoCo ≥ 80 %, ArchUnit, Spotless) | CI + `/gate`       | Máquina               |
| Mecánico | `frontend` (lint, prettier, typecheck, Vitest)               | CI + `/gate`       | Máquina               |
| Mecánico | `evals` (fake) · `e2e` (compose + Playwright)                | CI + `/gate --e2e` | Máquina               |
| Mecánico | `security` (CodeQL, dependency-review, gitleaks)             | CI                 | Máquina               |
| Mecánico | **`ci-ok`** (agregador, único check requerido)               | CI                 | Máquina               |
| Juicio   | Etiqueta `revisor:aprobado`                                  | PR                 | Subagente `revisor`   |
| Juicio   | Etiqueta `seguridad:ok`                                      | PR                 | Subagente `seguridad` |

El merge solo ocurre con `scripts/merge-si-verde.mjs` (`/merge-si-verde <pr>`), que exige `ci-ok` en SUCCESS y ambas etiquetas puestas después del último commit.

## Capa Claude Code (`.claude/`)

### Hooks (`.claude/hooks/*.mjs`, Node puro, con tests en `.claude/hooks/test/`)

| Hook               | Evento                                   | Qué hace                                                                                            |
| ------------------ | ---------------------------------------- | --------------------------------------------------------------------------------------------------- |
| `guard-secrets`    | PreToolUse (Edit/Write, Bash/PowerShell) | Bloquea escribir o leer `.env*` (salvo `.env.example`), llaves privadas y credenciales.             |
| `guard-git-push`   | PreToolUse (Bash/PowerShell)             | Bloquea `push --force`, push a `main`, push de tags y `gh pr merge` fuera del script.               |
| `format-on-edit`   | PostToolUse (Edit/Write)                 | Prettier vía API y Spotless con `-DspotlessFiles`; ignora `generated/`.                             |
| `bitacora-on-stop` | Stop                                     | Avisa si `.mada/fallas.jsonl` tiene fallas más nuevas que la bitácora; aplica la regla de 3 fallas. |
| `session-context`  | SessionStart                             | Imprime `.mada/estado.json` para retomar tras compactar.                                            |

### Subagentes (`.claude/agents/`)

`arquitecto` (opus), `redactor-specs`, `dev-backend`, `dev-frontend`, `qa`, `devops` (sonnet), `seguridad` y `revisor` (opus). Cada uno con herramientas mínimas y rutas propias según `AGENTS.md`.

### Skills

- `nueva-capacidad`: procedimiento MADA completo para una capacidad (C1–C4) con los comandos OpenSpec verificados.
- `adr`: plantilla y numeración automática de ADRs.
- `openspec-*` y `/opsx:*`: generados por OpenSpec, no se editan.

### Comandos (`.claude/commands/`)

| Comando                      | Qué hace                                                                        |
| ---------------------------- | ------------------------------------------------------------------------------- |
| `/gate [--rapido\|--e2e]`    | Corre `scripts/gate.mjs` y resume cada paso; si falla, registra en la bitácora. |
| `/merge-si-verde <pr>`       | Corre `scripts/merge-si-verde.mjs <pr>`.                                        |
| `/bitacora`                  | Agrega una fila a `docs/bitacora-fallas.md`.                                    |
| `/evidencia <cambio>`        | Genera capturas Playwright y las enlaza en el PR.                               |
| `/revisar-pr <pr>`           | Invoca `revisor` y luego `seguridad` sobre el PR.                               |
| `/fase inicio\|fin <F#\|Cn>` | Registra tiempos en `.mada/fases.jsonl` y actualiza `.mada/estado.json`.        |

### MCP y Browser pane

- `.mcp.json`: Playwright MCP (lo usa `qa`).
- `.claude/launch.json`: `frontend-dev` (Vite en 5173) y `compose` (stack en 8080).

## Trabajo en paralelo con worktrees (F4)

1. En `feat/cN` se commitean primero el contrato y el cliente generado.
2. `dev-backend` y `dev-frontend` trabajan en paralelo, cada uno en un **worktree aislado** creado desde `feat/cN`.
3. Al terminar: `node scripts/check-ownership.mjs` sobre cada rama; merge `--no-ff` en `feat/cN`.
4. `qa` escribe E2E y evidencia; `/gate --e2e` en segundo plano.
5. `/revisar-pr`; se corrigen hallazgos verificados; `/merge-si-verde`.
6. Las verificaciones pesadas (Testcontainers, compose) se **serializan** (Docker tiene 8 GB).

## Bitácora

Toda falla de gate, error de agente, herramienta ausente o reintento se registra en `docs/bitacora-fallas.md` (formato de tabla, numeración correlativa, la más reciente al final). Usar `/bitacora`. Si el mismo gate falla 3 veces: registrar y **parar**.

## Notas Windows

- Todo spawn desde Node pasa por `scripts/lib/run.mjs` (`run`, `capture`, `runAsync`, `mvnw`): usa `shell:true` y cita argumentos, porque Node 22 no ejecuta `.cmd` con `shell:false` y la ruta del repo tiene un espacio (`Sistemas IA`).
- Citar siempre rutas en comandos (`"$CLAUDE_PROJECT_DIR/..."`).
- Finales de línea LF (`.gitattributes`); los `.cmd` en CRLF. Comparaciones de archivos generados normalizan CRLF.
- `node --test` necesita un glob entre comillas, no un directorio.

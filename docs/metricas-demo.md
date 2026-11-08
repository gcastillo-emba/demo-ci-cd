# Métricas de la demo — POC Asuntos & Horas

Métricas de F6 sobre la construcción agéntica del POC (metodología MADA-MB, Claude Code). Las tablas del bloque generado salen de [`scripts/metricas.mjs`](../scripts/metricas.mjs) y son reproducibles. El resto del documento se redactó a mano a partir de esas tablas, de la [bitácora de fallas](bitacora-fallas.md) y del [runbook](runbook.md).

## Cómo regenerar

```bash
node scripts/metricas.mjs --salida docs/metricas-demo.md --mada "<checkout principal>/.mada" --gate .gate/last-run.json
```

- Fuentes: `gh pr list`, `gh run list` y `gh run view` (jobs fallidos), comentarios `VEREDICTO:` y `## Revisión de seguridad` de los PRs, el artefacto `jacoco-report` del último CI verde de `main`, `docs/bitacora-fallas.md`, `.mada/fases.jsonl`, `.mada/f0-start` y `.gate/last-run.json`.
- `.mada/` y `.gate/` están en `.gitignore`: viven en el checkout donde se trabajó. Por eso se pasan con `--mada` y `--gate` (este último se puede repetir, uno por worktree).
- El script solo reemplaza el bloque entre los marcadores `metricas:inicio` y `metricas:fin`. Sin `--salida`, imprime el bloque por la salida estándar.

## Resumen

| Métrica                                    | Valor                                                                                                                         | Fuente                                                       |
| ------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| Duración total (F0 → C4 en revisión)       | ≈ 8 h 40 min de reloj (2026-11-08 01:41 → 10:20 UTC), con fases solapadas por trabajo en paralelo                             | `.mada/f0-start`, `.mada/fases.jsonl`, PRs                   |
| PRs del agente fusionados                  | 10 en `main` antes de F6 (5 `feat`, 2 `fix`, 2 `docs`, 2 `chore`, contando #1)                                                | tabla "Pull requests"                                        |
| PRs reemplazados                           | 2 (#11 → #13 y #12 → #14), por rebase sin `push --force`                                                                      | bitácora, fila 44                                            |
| PRs de Dependabot                          | 7 (3 cerrados por Dependabot al quedar obsoletos, 4 abiertos en triage)                                                       | tabla "Pull requests"                                        |
| Jobs de CI fallidos en ramas del agente    | 0 de CI mecánico, 3 de `semantic-title`, 1 de CodeQL por infraestructura (504)                                                | tabla "Fallas de gates mecánicos en CI"                      |
| Alertas de CodeQL que bloquearon un PR     | 3 (PR #1 falso positivo, PR #5 CSRF acotado, PR #13 archivo temporal inseguro, este último real)                              | bitácora, filas 11, 19 y 43                                  |
| Gates de juicio con CAMBIOS SOLICITADOS    | 5 veredictos en 4 PRs (#1 dos veces, #2, #13, #20)                                                                            | tabla "Gates de juicio"                                      |
| Etiquetas `seguridad:ok` retenidas         | 2 (PRs #19 y #20, por SAST no ejecutado tras un 504)                                                                          | tabla "Gates de juicio"                                      |
| Filas en la bitácora                       | 48 al cierre de F6                                                                                                            | [bitácora](bitacora-fallas.md)                               |
| Cobertura JaCoCo `domain` + `application`  | 100 % de líneas (umbral del gate: 80 %); ver instrucciones y ramas en la tabla generada                                       | artefacto `jacoco-report` del CI de `main`                   |
| Evals del asistente (C4, adaptador `fake`) | 10/10 casos en verde                                                                                                          | job `evals` del PR #20, `evals/description-assistant/cases/` |
| Gate local completo con E2E                | 235–255 s (C3 y C4); el paso más lento es `backend` (≈ 110 s)                                                                 | `.gate/last-run.json` de cada worktree                       |
| Arranque de la demo en frío (`demo:check`) | **87,5 s** (≈ 88 s) desde imágenes de GHCR sin caché hasta `healthy`; 46,6 s con imágenes descargadas. Meta del plan: < 5 min | [runbook](runbook.md), README                                |
| Intervenciones humanas                     | 6 decisiones tomadas y 1 pendiente (tag `v0.1.0`)                                                                             | sección "Intervenciones humanas"                             |

## Intervenciones humanas

Las cuenta el orquestador: no se pueden derivar de `gh`. Solo se incluyen las decisiones que tomó el usuario. Los pedidos rutinarios de permiso de herramientas no cuentan.

| #   | Fase | Decisión del usuario                                                                                                                                           | Por qué no la tomó el agente                                                        |
| --- | ---- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| 1   | F0   | Aprobar el plan maestro que salió de plan mode.                                                                                                                | Plan mode exige aprobación explícita antes de escribir.                             |
| 2   | F0   | **Hacer público el repositorio**, para tener ruleset, CodeQL, dependency review y secret scanning sin costo (bitácora, fila 1; ADR-005).                       | Cambio de visibilidad: acción irreversible y externa (regla de parada 3).           |
| 3   | F0   | **Trabajar sin `ANTHROPIC_API_KEY`**: `claude-review.yml` y `evals-real.yml` quedan inertes (fila 2).                                                          | Falta un secreto (regla de parada 2) y usar la API tiene costo (regla de parada 1). |
| 4   | F4   | **Autorizar en el chat** que el subagente `revisor` publique su veredicto y etiquete los PRs.                                                                  | Publicar en un PR es una acción externa.                                            |
| 5   | F4   | **Aprobar la regla `allow`** en `.claude/settings.local.json`, después de que el clasificador del modo auto bloqueara `gh pr comment`/`gh pr edit` (fila 48).  | La autorización del punto 4 no alcanzó: el clasificador exige una regla de permiso. |
| 6   | F4   | **Acordar la regla de sesiones enlazadas**: una sesión por PR independiente, cada una en su worktree (ver la [propuesta de mejora](propuesta-mejora-mada.md)). | Cambio de metodología.                                                              |
| 7   | F5   | **Pendiente:** crear y empujar el tag `v0.1.0`, que dispara `release.yml`.                                                                                     | `guard-git-push` bloquea el push de tags del agente (regla de parada 3).            |

## Lectura de los números

- **El gate local funciona como filtro.** Ningún PR del agente falló un job de CI mecánico (backend, frontend, contrato, E2E). Todas las fallas de ese tipo están en ramas de Dependabot, que no pasan por el gate local. El costo se paga antes: 13 filas de la bitácora son fallas del gate local que nunca llegaron a CI.
- **Los checks rojos de CI en ramas del agente** son de dos tipos: títulos de PR con scopes no permitidos (barato de corregir) y CodeQL, con alertas o con una falla de infraestructura.
- **C2 (PR #16) tardó 3 h 57 min entre abierto y fusionado**, contra 7–32 min de los demás. El código estaba listo a las 05:43 (`fin` en `fases.jsonl`). La espera se debió al bloqueo del clasificador sobre el veredicto del revisor (intervenciones 4 y 5), no a trabajo técnico.
- **C3 aparece con 4 h 32 min** porque `fases.jsonl` no registró su `fin` y el script toma el merge del PR #19. Ese intervalo incluye la misma espera de C2, ya que C3 se construyó en paralelo.
- **Los gates de juicio encontraron defectos reales.** Los 5 CAMBIOS SOLICITADOS señalaron problemas que los gates mecánicos no veían, o que veían pero nadie había atendido: control de merge con fecha del autor y fail-open (PR #1, fila 9), tablas de trazabilidad faltantes en dos specs (PR #2), CodeQL y título en rojo antes de pedir revisión (PR #13), y redacción de PII aplicada tarde en el asistente (PR #20). Ver el [informe](informe-demo.md).

## Tablas generadas

<!-- metricas:inicio -->

> Generado con `node scripts/metricas.mjs` el 2026-11-08 10:44 UTC sobre `gcastillo-emba/demo-ci-cd`. No editar a mano entre los marcadores.

### Tiempo por fase

| Fase  | Inicio (UTC)     | Fin (UTC)        | Duración   | Fuente del fin            | PR  | PR abierto → merge |
| ----- | ---------------- | ---------------- | ---------- | ------------------------- | --- | ------------------ |
| F0–F1 | 2026-11-08 01:41 | 2026-11-08 02:00 | 19 min     | `fin` en fases.jsonl      | #1  | 18 min             |
| F2    | 2026-11-08 02:00 | 2026-11-08 02:12 | 12 min     | `fin` en fases.jsonl      | #2  | 17 min             |
| F3    | 2026-11-08 02:12 | 2026-11-08 03:43 | 1 h 31 min | merge del PR #5           | #5  | 18 min             |
| C1    | 2026-11-08 03:35 | 2026-11-08 04:26 | 51 min     | merge del PR #10          | #10 | 7 min              |
| C2    | 2026-11-08 04:28 | 2026-11-08 05:43 | 1 h 15 min | `fin` en fases.jsonl      | #16 | 3 h 57 min         |
| C3    | 2026-11-08 05:43 | 2026-11-08 10:15 | 4 h 32 min | merge del PR #19          | #19 | 32 min             |
| C4    | 2026-11-08 09:28 | —                | —          | en curso (PR #20 abierto) | #20 | —                  |

### Pull requests

| Tipo                | Cantidad |
| ------------------- | -------- |
| chore               | 2        |
| ci                  | 1        |
| dependabot (closed) | 3        |
| dependabot (open)   | 4        |
| docs                | 2        |
| feat                | 5        |
| fix                 | 2        |
| reemplazado         | 2        |

Total: 21 PRs.

| PR  | Título                                                                                                                                   | Tipo        | Estado | Abierto (UTC)    | Abierto → merge |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------- | ----------- | ------ | ---------------- | --------------- |
| #1  | chore: encuadre y contexto MADA-MB (F0/F1)                                                                                               | chore       | merged | 2026-11-08 01:59 | 18 min          |
| #2  | docs(spec): propuestas OpenSpec C1–C4                                                                                                    | docs        | merged | 2026-11-08 02:18 | 17 min          |
| #3  | chore(deps): Bump the actions group with 6 updates                                                                                       | dependabot  | closed | 2026-11-08 02:18 | —               |
| #4  | chore(deps): Bump the dev-tooling group with 2 updates                                                                                   | dependabot  | closed | 2026-11-08 02:18 | —               |
| #5  | feat(skeleton): walking skeleton de punta a punta y pipeline CI/CD completo                                                              | feat        | merged | 2026-11-08 03:26 | 18 min          |
| #6  | chore(deps): Bump nginxinc/nginx-unprivileged from 1.30-alpine to 1.31-alpine in /frontend in the docker-images group across 1 directory | dependabot  | open   | 2026-11-08 03:44 | —               |
| #7  | chore(deps): Bump the backend group across 1 directory with 7 updates                                                                    | dependabot  | open   | 2026-11-08 03:45 | —               |
| #8  | chore(deps): Bump the actions group across 1 directory with 15 updates                                                                   | dependabot  | open   | 2026-11-08 03:45 | —               |
| #9  | chore(deps): Bump the dev-tooling group across 1 directory with 13 updates                                                               | dependabot  | closed | 2026-11-08 03:46 | —               |
| #10 | feat(c1): clientes y asuntos                                                                                                             | feat        | merged | 2026-11-08 04:20 | 7 min           |
| #11 | chore(skills): skills nueva-capacidad y adr; candado E2E entre worktrees                                                                 | reemplazado | closed | 2026-11-08 04:23 | —               |
| #12 | fix(skeleton): endurecimiento de autenticación y hallazgos de revisión F3                                                                | reemplazado | closed | 2026-11-08 04:23 | —               |
| #13 | chore(ci): skills nueva-capacidad y adr; candado E2E entre worktrees                                                                     | chore       | merged | 2026-11-08 04:27 | 12 min          |
| #14 | fix(skeleton): endurecimiento de autenticación y hallazgos de revisión F3                                                                | fix         | merged | 2026-11-08 04:44 | 7 min           |
| #15 | docs(release): runbook de demo, README y resumen headless de la bitácora                                                                 | docs        | merged | 2026-11-08 05:33 | 6 min           |
| #16 | feat(c2): registro de horas                                                                                                              | feat        | merged | 2026-11-08 05:41 | 3 h 57 min      |
| #17 | fix(ci): headless sin herramientas con bitácora por stdin; guards solo booleanos                                                         | fix         | merged | 2026-11-08 05:42 | 3 h 56 min      |
| #18 | chore(deps): Bump the dev-tooling group across 1 directory with 14 updates                                                               | dependabot  | open   | 2026-11-08 05:42 | —               |
| #19 | feat(c3): tablero de horas                                                                                                               | feat        | merged | 2026-11-08 09:43 | 32 min          |
| #20 | feat(c4): asistente de descripción con IA (feature flag)                                                                                 | feat        | open   | 2026-11-08 10:20 | —               |
| #21 | ci: migrar codeql-action a v4                                                                                                            | ci          | open   | 2026-11-08 10:31 | —               |

### Corridas de GitHub Actions

| Workflow           | Corridas | success | failure | cancelled | skipped |
| ------------------ | -------- | ------- | ------- | --------- | ------- |
| CD                 | 8        | 8       | 0       | 0         | 0       |
| CI                 | 42       | 33      | 6       | 2         | 0       |
| Claude review      | 25       | 0       | 0       | 0         | 25      |
| Dependabot Updates | 15       | 12      | 3       | 0         | 0       |
| Labels reset       | 9        | 9       | 0       | 0         | 0       |
| PR title           | 46       | 43      | 3       | 0         | 0       |
| Security           | 42       | 40      | 2       | 0         | 0       |

Reintentos (corridas con `attempt` > 1, p. ej. `gh run rerun --failed`): **2**.

### Fallas de gates mecánicos en CI

| Tipo                         | Jobs fallidos en ramas del agente | Jobs fallidos en ramas de Dependabot |
| ---------------------------- | --------------------------------- | ------------------------------------ |
| CI mecánico                  | 0                                 | 15                                   |
| CodeQL                       | 1                                 | 2                                    |
| Dependabot (infraestructura) | 0                                 | 3                                    |
| semantic-title               | 3                                 | 0                                    |

Las fallas de CodeQL por **alertas** (no por jobs caídos) se publican como el check `CodeQL` del commit y no aparecen aquí; ver la bitácora.

| Fecha (UTC)      | Workflow           | Rama                                             | Intento | Jobs fallidos                                        |
| ---------------- | ------------------ | ------------------------------------------------ | ------- | ---------------------------------------------------- |
| 2026-11-08 02:18 | CI                 | `dependabot/npm_and_yarn/dev-tooling-7899f30c1a` | 1       | tooling, ci-ok                                       |
| 2026-11-08 03:44 | Dependabot Updates | `main`                                           | 1       | Dependabot                                           |
| 2026-11-08 03:45 | CI                 | `dependabot/maven/backend/backend-990c4ce595`    | 1       | backend, ci-ok                                       |
| 2026-11-08 03:46 | CI                 | `dependabot/npm_and_yarn/dev-tooling-3a7ad0461b` | 1       | spec, tooling, ci-ok                                 |
| 2026-11-08 04:23 | PR title           | `chore/skills`                                   | 1       | semantic-title                                       |
| 2026-11-08 04:23 | PR title           | `fix/endurecimiento-auth`                        | 1       | semantic-title                                       |
| 2026-11-08 04:26 | Dependabot Updates | `main`                                           | 1       | Dependabot                                           |
| 2026-11-08 04:27 | PR title           | `chore/skills-v2`                                | 1       | semantic-title                                       |
| 2026-11-08 04:51 | Dependabot Updates | `main`                                           | 1       | Dependabot                                           |
| 2026-11-08 04:54 | CI                 | `dependabot/maven/backend/backend-990c4ce595`    | 1       | backend, ci-ok                                       |
| 2026-11-08 05:43 | CI                 | `dependabot/npm_and_yarn/dev-tooling-bac7b80a83` | 1       | spec, tooling, ci-ok                                 |
| 2026-11-08 10:19 | Security           | `dependabot/npm_and_yarn/dev-tooling-bac7b80a83` | 1       | codeql (java-kotlin), codeql (javascript-typescript) |
| 2026-11-08 10:19 | CI                 | `dependabot/npm_and_yarn/dev-tooling-bac7b80a83` | 1       | spec, tooling, ci-ok                                 |
| 2026-11-08 10:20 | Security           | `feat/c4`                                        | 2       | codeql (javascript-typescript)                       |

### Gates de juicio (comentarios de veredicto)

| PR  | revisor: CAMBIOS SOLICITADOS | revisor: APROBADO | seguridad: rondas | seguridad: etiqueta retenida |
| --- | ---------------------------- | ----------------- | ----------------- | ---------------------------- |
| #1  | 2                            | 1                 | 3                 | 0                            |
| #2  | 1                            | 1                 | 2                 | 0                            |
| #5  | 0                            | 1                 | 1                 | 0                            |
| #10 | 0                            | 1                 | 1                 | 0                            |
| #13 | 1                            | 1                 | 2                 | 0                            |
| #14 | 0                            | 1                 | 1                 | 0                            |
| #15 | 0                            | 1                 | 1                 | 0                            |
| #16 | 0                            | 1                 | 1                 | 0                            |
| #17 | 0                            | 1                 | 1                 | 0                            |
| #19 | 0                            | 1                 | 2                 | 1                            |
| #20 | 1                            | 0                 | 1                 | 1                            |
| #21 | 0                            | 1                 | 1                 | 0                            |

Totales: 5 CAMBIOS SOLICITADOS, 11 APROBADO, 2 etiquetas `seguridad:ok` retenidas por SAST incompleto.

### Bitácora de fallas

| Fase | Filas |
| ---- | ----- |
| F0   | 6     |
| F1   | 5     |
| F3   | 12    |
| C1   | 3     |
| C2   | 6     |
| C3   | 2     |
| F4   | 7     |

| Categoría (heurística por palabras clave) | Filas |
| ----------------------------------------- | ----- |
| gate mecánico local                       | 13    |
| entorno / dependencias                    | 8     |
| spec o instrucción                        | 6     |
| CodeQL                                    | 5     |
| herramienta / Claude Code                 | 3     |
| concurrencia entre worktrees              | 3     |
| gate de juicio                            | 2     |
| semantic-title                            | 1     |

Total: 41 filas.

### Cobertura JaCoCo (`domain` + `application`)

| Instrucciones | Ramas  | Líneas  | Fuente                                                       |
| ------------- | ------ | ------- | ------------------------------------------------------------ |
| 99.8 %        | 98.9 % | 100.0 % | artefacto `jacoco-report` del CI 42470651592 (main, 4e48fe5) |

### Gate local (`.gate/last-run.json`)

| Archivo             | Fecha (UTC)      | Modo     | Total   | Paso más lento    | Resultado |
| ------------------- | ---------------- | -------- | ------- | ----------------- | --------- |
| f5                  | 2026-11-08 05:32 | --rapido | 126.0 s | backend (52.5 s)  | todo PASS |
| c3                  | 2026-11-08 09:43 | --e2e    | 235.2 s | backend (112.7 s) | todo PASS |
| c4                  | 2026-11-08 10:38 | --rapido | 184.1 s | evals (66.1 s)    | todo PASS |
| cranky-tharp-c82b0b | 2026-11-08 10:43 | --rapido | 124.8 s | backend (50.7 s)  | todo PASS |

<!-- metricas:fin -->

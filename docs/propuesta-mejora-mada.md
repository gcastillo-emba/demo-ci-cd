# Propuesta de mejora de MADA-MB

Mejoras a la metodología que salen de la [bitácora de fallas](bitacora-fallas.md) (citada como "fila N"), del [informe de la demo](informe-demo.md) y de las [métricas](metricas-demo.md). Cada propuesta indica la falla que la origina y dónde se aplica.

## 1. Gates: redundantes, faltantes y ajustes

### 1.1 Gates redundantes o de bajo rendimiento

| Gate                                                   | Observación                                                                                                                                                                          | Propuesta                                                                                                                                                                                                                                                                                       |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| CI mecánico completo en ramas del agente               | Ningún job de CI mecánico (backend, frontend, contrato, E2E) falló en un PR del agente: el gate local `--e2e` ya los había filtrado. Todas esas fallas de CI vinieron de Dependabot. | **Mantenerlo**: es la red para quien no corre el gate local (Dependabot, humanos) y la prueba en Linux (fila 17). No es redundante para la máquina, pero sí para el agente: no hace falta esperar CI para pedir la revisión de juicio si el gate `--e2e` local está verde y el árbol no cambió. |
| `claude-review.yml`                                    | Inerte toda la demo (sin `ANTHROPIC_API_KEY`); 24 corridas `skipped`. Duplica lo que hace el subagente `revisor`.                                                                    | Quitarlo de los PRs del agente; dejarlo solo para PRs humanos cuando haya secreto. Un gate que nunca corre ensucia la lista de checks.                                                                                                                                                          |
| Revisor corriendo el gate completo (fila 7)            | Se pidió en F1 porque los autores entregaban sin formato. Desde `format-on-edit`, el revisor repite el gate que ya corrió el autor.                                                  | El revisor verifica el `.gate/last-run.json` del SHA de cabecera y los checks de CI; solo vuelve a correr el gate si el SHA no coincide. Los casos adversariales siguen siendo suyos.                                                                                                           |
| Doble revisión de títulos (revisor + `semantic-title`) | El revisor bloqueó el PR #13 por el título, que ya estaba en rojo en CI.                                                                                                             | El revisor no revisa nada que tenga gate mecánico en rojo: devuelve "corregir checks primero" sin gastar una ronda de juicio.                                                                                                                                                                   |

### 1.2 Gates faltantes

| Gate propuesto                                     | Falla que lo origina                                                                                                                  | Dónde                                                                                                                                                                |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Prueba de que un gate nuevo puede fallar**       | ArchUnit reportó 0 tests y pasó en verde sin evaluar nada (fila 14).                                                                  | Checklist del revisor y de `devops`: cada gate nuevo se entrega con un caso que **debe** fallar; `gate.mjs` trata "0 tests" como falla.                              |
| **Gate no ejecutado ≠ verde**                      | CodeQL con 504 (fila 51); el subagente `seguridad` lo detectó a mano.                                                                 | `merge-si-verde.mjs`: exigir que los jobs de CodeQL de ambos lenguajes estén en `success` para el SHA de cabecera, no solo `ci-ok`.                                  |
| **Aritmética de escenarios límite en spec-review** | Escenario imposible en C2 (10 h + edición a 14 h = 24 h, que el límite inclusivo admite; fila 27); `validate --strict` no lo ve.      | Checklist del `revisor` en modo spec-review: recalcular cada escenario numérico contra los bordes inclusivos/exclusivos de los demás escenarios del mismo requisito. |
| **Orden de migraciones repetibles**                | Seed `R__` que corre antes que su dependencia por orden alfabético (fila 29).                                                         | Test de backend que arranca Flyway sobre una base vacía con el perfil `demo` (ya lo cubre el IT si existe con ese perfil; hacerlo explícito).                        |
| **Evidencia filtrada por capacidad**               | La evidencia de C2 reescribió los PNG archivados de C1 (fila 30).                                                                     | `npm run evidence -- <cambio>` falla si modifica archivos fuera de `docs/evidence/<cambio>/`.                                                                        |
| **Gate completo tras cada rebase**                 | Pruebas que leen el contrato completo detectaron `code` nuevos de otra rama (fila 32); test inestable expuesto tras rebase (fila 26). | Skill `nueva-capacidad` y orquestador: después de cada rebase, `npm run gate` antes de pedir revisión.                                                               |

## 2. Reglas nuevas para la metodología

### 2.1 Una sesión enlazada por PR independiente (acordada con el usuario)

> **Regla.** Cada PR independiente se trabaja en **una sesión enlazada propia, en su propio worktree**, creada por el orquestador desde `origin/main`. Los **subagentes y los workflows** se usan **solo para paralelismo dentro del mismo cambio** (backend ∥ frontend, revisor y seguridad). El **orquestador** coordina el **orden de merge** y los **rebases `--onto`** cuando un PR depende de otro.

Origen: PRs apilados que hubo que reemplazar (fila 48), colisiones entre flujos paralelos (filas 22, 25, 49) y contexto del orquestador saturado por trabajo independiente. Desde F5 se aplicó así (runbook, fixes, migración de CodeQL, triage de Dependabot, F6) sin PRs reemplazados.

Consecuencias:

- Una sesión no toca la rama de otra. Si su PR depende de otro, espera el merge y rebasa con `git rebase --onto origin/main <base-vieja> <rama>` en una **rama nueva** (el force push sigue prohibido).
- Las sesiones informan al orquestador al terminar; el orquestador decide el orden de `merge-si-verde` y avisa a las dependientes para que rebasen.
- Los recursos de máquina (compose, puertos) siguen protegidos por el candado (§3.5), sin importar cuántas sesiones haya.

### 2.2 Provisionar en F0 los permisos de los gates de juicio

Origen: el clasificador del modo auto bloqueó `gh pr comment`/`gh pr edit` del revisor aun con autorización en el chat (fila 52); C2 esperó ≈ 4 h.

- En F0, agregar a `.claude/settings.json` (versionado, no `settings.local.json`) reglas `allow` mínimas y acotadas: `gh pr comment`, `gh pr edit --add-label revisor:aprobado|seguridad:ok`, `gh pr view`, `gh pr checks`, `gh api` de solo lectura sobre el repo.
- Nunca `gh pr merge` (lo bloquea `guard-git-push`; el merge es de `merge-si-verde.mjs`).
- La checklist de F0 incluye un **ensayo** del gate de juicio sobre un PR de prueba antes de F2.

### 2.3 Crear la capa `.claude/` antes de la sesión orquestadora

Origen: subagentes no disponibles a mitad de sesión (fila 8). La capa (agentes, hooks, settings, skills) se commitea en F0/F1 y la sesión orquestadora se abre después.

### 2.4 Numeración de la bitácora con PRs en paralelo

Origen: con varios PRs abiertos a la vez, cada uno agrega filas "al final" con el siguiente número que ve en su rama; los números chocan al fusionar (C4 terminó usando 35–42 y las filas de F6, escritas como 42–48, hubo que renumerarlas a 43–49 al rebasar), y algunas fallas quedaron sin registrar hasta F6 (`.mada/pendientes-bitacora.md`).

Propuesta (elegir una en el próximo F0):

- **A. Numerar al fusionar (recomendada).** En la rama, las filas nuevas llevan `#` = `?`. `merge-si-verde.mjs` (o un paso previo del orquestador) asigna los números correlativos sobre `main` justo antes del squash y hace el commit. Mantiene la tabla única y legible.
- **B. ID por PR.** `#` = `PR-<n>.<k>` (por ejemplo `PR-20.3`). Sin conflictos ni pasos extra, a costa de perder la correlación global. El script de métricas ordena por fecha.

En ambos casos, cada sesión registra sus fallas en su propio PR (no en un archivo local pendiente).

### 2.5 Enmiendas de ADR

Origen: ADR-006/ADR-003 editados in situ (fila 50). Ya aplicado en F6: `docs/adr/README.md` distingue **enmienda** (línea `Revisado: fecha (PR #N)`) de **cambio de decisión** (ADR nuevo que reemplaza). La skill `adr` debe ofrecer el modo "enmienda".

### 2.6 Las instrucciones citan, no parafrasean

Origen: nombres de migración y seed distintos a los de `tasks.md` (fila 20), scopes inventados (fila 46), `useFeatures()` "que ya existía" pero no existía (fila 39), clave del indicador con dos grafías (fila 41).

- El prompt del orquestador enlaza los artefactos (`tasks.md`, `design.md`, AGENTS.md §5 para los scopes) en vez de resumirlos; ante conflicto gana el artefacto.
- El `redactor-specs` verifica contra el código cada afirmación "ya existe" del design.

### 2.7 Registrar el fin de cada fase de forma automática

Origen: `fases.jsonl` sin `fin` para F3, C1 y C3; el script de métricas debe inferirlos. `merge-si-verde.mjs` llama a `scripts/fase.mjs fin <fase>` al fusionar el PR asociado a una fase.

## 3. Plantillas para reutilizar

| Plantilla                                                                     | Qué resuelve                                                                                                                                                                                                                             | Cómo reutilizarla                                                                                                                                                                                  |
| ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Skill [`nueva-capacidad`](../.claude/skills/nueva-capacidad/SKILL.md)**     | Procedimiento MADA completo de una capacidad: contrato → backend ∥ frontend en worktrees → integración → evidencia → archivo → PR → merge. Destilada de C1, usada en C2–C4.                                                              | Copiar a cada proyecto; actualizar la lista de scopes y los comandos del arnés. Agregar el paso "gate completo tras cada rebase".                                                                  |
| **Workflow parametrizado `mada-capacidad`**                                   | La skill hecha script: fases Contract → Build (dos agentes en paralelo en worktrees) → Integrate, con argumentos `n`, `id`, `cap` y extras por capa. Corrió C2–C4.                                                                       | Hoy vive en el directorio de sesión de Claude Code, no en el repo. **Versionarlo** en `.claude/workflows/mada-capacidad.js` para que cualquier sesión lo invoque por nombre y quede bajo revisión. |
| **[`scripts/merge-si-verde.mjs`](../scripts/merge-si-verde.mjs)**             | Merge por agente fail-closed: `ci-ok` en SUCCESS, etiquetas posteriores al inicio del `ci-ok` del head (hora del servidor), `VEREDICTO: APROBADO` con el SHA, autores `OWNER`/`MEMBER`/`COLLABORATOR`, squash con `--match-head-commit`. | Reutilizar tal cual con `labels-reset.yml`. Extensiones propuestas: exigir CodeQL ejecutado (§1.2), numerar la bitácora (§2.4) y registrar el fin de fase (§2.7).                                  |
| **Hooks [`.claude/hooks/`](../.claude/hooks/)**                               | `guard-secrets`, `guard-git-push`, `format-on-edit`, `bitacora-on-stop`, `session-context`, con tests adversariales.                                                                                                                     | Copiar con sus tests; ajustar rutas y formateadores. Mantener el patrón: Node puro, sin dependencias, probado con formas anidadas (fila 6, fila 7).                                                |
| **Candado entre worktrees [`scripts/lib/lock.mjs`](../scripts/lib/lock.mjs)** | Serializa compose/E2E/demo entre worktrees y sesiones: directorio por usuario 0700, `owner.json` con `wx`/0600, abandono por PID muerto o antigüedad, retiro atómico por `rename`.                                                       | Usarlo para todo recurso de máquina compartido (Docker, puertos, bases locales). Tomarlo dentro del script (`npm run e2e`), nunca por instrucción.                                                 |
| **[`scripts/lib/run.mjs`](../scripts/lib/run.mjs)**                           | Spawn multiplataforma con `shell:true` y citado de argumentos (Windows, rutas con espacios, `.cmd`).                                                                                                                                     | Primer archivo de cualquier repo con scripts Node en Windows.                                                                                                                                      |
| **[`scripts/metricas.mjs`](../scripts/metricas.mjs)**                         | Métricas reproducibles de la retro desde `gh`, la bitácora y `.mada/`.                                                                                                                                                                   | Ajustar `PR_DE_FASE` y las heurísticas de categorías; correr al cierre de cada fase, no solo en F6.                                                                                                |

## 4. Checklist de F0 propuesta (resumen)

1. Plan y visibilidad de GitHub verificados (fila 1); secretos provistos o modo degradado explícito (fila 2).
2. Subcomandos reales de cada CLI verificados (filas 3, 5) y scripts npm probados en Windows (fila 4).
3. Capa `.claude/` commiteada **antes** de la sesión orquestadora (fila 8), con reglas `allow` de los gates de juicio y un ensayo sobre un PR de prueba (fila 52).
4. Acciones fijadas por SHA desde el primer workflow (fila 10).
5. Candado de recursos compartidos y `run.mjs` desde el primer commit (filas 16, 49).
6. Regla de sesiones (§2.1) y esquema de numeración de la bitácora (§2.4) decididos.

---
description: Corre los gates de juicio sobre un PR, primero el subagente revisor y luego el subagente seguridad, y reporta ambos veredictos.
argument-hint: '<pr>'
allowed-tools: Agent, Task, Bash(gh pr view:*), Bash(gh pr checks:*)
---

PR: `$ARGUMENTS` (si falta, usa el PR de la rama actual con `gh pr view --json number`).

1. Lee el PR (`gh pr view $ARGUMENTS --json number,title,headRefName,headRefOid,labels`) e identifica el cambio OpenSpec asociado. Si el título es `docs(spec): …`, el revisor usa el **modo spec-review**.
2. Invoca el subagente **`revisor`** sobre el PR. Debe publicar `VEREDICTO: APROBADO|CAMBIOS SOLICITADOS` y poner `revisor:aprobado` solo si aprueba.
3. Invoca el subagente **`seguridad`** sobre el mismo PR. Debe publicar sus hallazgos con severidad y poner `seguridad:ok` solo si no hay Críticos ni Altos.
4. Ningún subagente que haya escrito parte del diff puede revisarlo.
5. Verifica de forma adversarial los hallazgos bloqueantes (que el archivo:línea exista y el problema sea real) antes de reportarlos.
6. Reporta:

| Gate      | Veredicto | Etiqueta | Bloqueantes |
| --------- | --------- | -------- | ----------- |
| revisor   | …         | …        | …           |
| seguridad | …         | …        | …           |

Si ambos aprueban y `ci-ok` está en verde, sugiere `/merge-si-verde $ARGUMENTS`; si no, lista las correcciones pendientes.

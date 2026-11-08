---
description: Genera capturas de evidencia con Playwright para un cambio y las enlaza en el cuerpo del PR.
argument-hint: '<cambio>'
allowed-tools: Bash(npm run evidence:*), Bash(gh pr view:*), Bash(gh pr edit:*), Bash(git branch:*), Bash(git rev-parse:*), Read, Glob
---

Cambio: `$ARGUMENTS` (si falta, pídelo; debe coincidir con un id en `openspec/changes/`).

1. Verifica que el stack esté arriba (`docker compose up --build --wait`, http://localhost:8080); si no lo está, indícalo y detente.
2. Ejecuta el proyecto de evidencia de Playwright:
   ```
   npm run evidence -- $ARGUMENTS
   ```
3. Lista `docs/evidence/$ARGUMENTS/*.png`. Si no hay capturas, regístralo con `/bitacora` y detente.
4. Obtén el PR de la rama actual (`gh pr view --json number,body,headRefName`).
5. Agrega (o reemplaza si ya existe) una sección `## Evidencia` al cuerpo del PR con un enlace por captura, usando la ruta en la rama:
   `![<nombre>](https://github.com/gcastillo-emba/demo-ci-cd/blob/<rama>/docs/evidence/$ARGUMENTS/<archivo>.png?raw=true)`
   Escribe el cuerpo nuevo en un archivo temporal fuera del repo y aplícalo con `gh pr edit <n> --body-file <archivo>`. Conserva el resto del cuerpo.
6. Recuerda que las capturas deben estar commiteadas y pusheadas para que los enlaces funcionen.
7. Informa el número del PR y la lista de capturas enlazadas.

---
name: nueva-capacidad
description: Procedimiento MADA-MB completo para construir una capacidad (C2, C3, C4 o cualquier cambio OpenSpec ya aprobado) de punta a punta. El flujo es spec, contrato OpenAPI, backend ∥ frontend en worktrees, arnés, revisión, evidencia, archivo y merge. Úsala al empezar a implementar un cambio de openspec/changes/.
---

# Nueva capacidad (MADA-MB)

Procedimiento destilado de C1 (`add-clients-matters`, PR #10). Cada paso tiene un gate. No se avanza con un gate en rojo.

## 0. Precondiciones

- El cambio OpenSpec existe en `openspec/changes/<id>/`, se fusionó a `main` y pasa `openspec validate <id> --strict --no-interactive`.
- `main` está en verde: `gh run list --workflow CI --branch main --limit 1`.
- Hay que leer `proposal.md`, `design.md` (sobre todo `## Supuestos` y `## Trazabilidad`), `specs/**/spec.md` y `tasks.md`. **La spec es la fuente de verdad.** Si el código y la spec discrepan, se corrige el código o se actualiza la spec (con su revisión), nunca en silencio.
- Registrar la fase: `node scripts/fase.mjs inicio Cn`.

## 1. Rama e integración

```bash
git fetch origin
git worktree add -b feat/cN ../wt/cN origin/main   # ruta corta, fuera de "Sistemas IA"
```

Los worktrees se crean en `C:\gcastillo\wt\…`, una ruta corta y sin espacios, para evitar MAX_PATH y problemas de comillas (bitácora #16–#18). Cada worktree necesita su propio `npm ci`.

## 2. Contrato primero (rol `redactor-specs`)

1. Se extiende `api/openapi.yaml` con la sección "1. Contrato" de `tasks.md`.
   - Un **tag por interfaz Java** que se quiera generar: el generador crea `<Tag>Api`.
   - Todo 4xx/5xx usa `application/problem+json` con `$ref` a `Problem`.
   - Hay que documentar el `code` de cada error.
2. Se corre `npm run api:lint && npm run api:generate && npm run api:check` y, en `backend/`, `mvnw -q -DskipTests compile`.
3. Commit propio: `feat(api): contrato Cn …`. **El contrato es el acuerdo** entre backend y frontend. `ContractConformanceIT` quedará en rojo hasta que existan los controllers; es lo esperado.

## 3. Backend ∥ frontend en worktrees separados

```bash
git worktree add -b feat/cN-backend  ../wt/cN-back  feat/cN
git worktree add -b feat/cN-frontend ../wt/cN-front feat/cN
```

Se lanzan **en paralelo**, como subagentes o pasos de Workflow:

- **dev-backend** en `cN-back`. Solo toca `backend/**`.
  - Migración Flyway `Vn__*.sql` y seed `R__*.sql` idempotente con datos claramente ficticios.
  - Dominio → puertos → servicios: rol y propiedad en `application`, para que cuenten en JaCoCo.
  - Adaptadores, `ProblemCatalog` y reglas por URL en `SecurityConfig` (precedencia 401 > 403 > 400 > 404 > 409).
  - Un IT por escenario, etiquetado `// Scenario: <nombre>`.
  - Gate: `mvnw spotless:apply` y luego `mvnw -B -ntp verify` en verde (JaCoCo ≥ 80 % en domain+application).
- **dev-frontend** en `cN-front`. Solo toca `frontend/**` y `e2e/**`, nunca `frontend/src/api/generated/**`.
  - Pantallas en español y accesibles: labels, `aria-live` y `<dialog>` nativo (en jsdom necesita polyfill, bitácora #21).
  - Hooks de React Query sobre el cliente tipado, mensajes por `code` de Problem y ocultamiento por rol.
  - Pruebas Vitest y un spec E2E. Spec `@evidencia` que guarda capturas en `docs/evidence/<id>/NN-*.png`.
  - Gate: `npm run frontend:lint && frontend:typecheck && frontend:test && frontend:build`.

Los builders **no editan** `tasks.md` ni la bitácora. Reportan los ids de tarea y las fallas para que el integrador las registre; así se evitan conflictos de merge.

## 4. Integración (roles `qa` y `devops`, worktree `cN`)

1. **Gate mecánico de propiedad**:
   ```bash
   node scripts/check-ownership.mjs feat/cN feat/cN-backend  --allow "backend/**"
   node scripts/check-ownership.mjs feat/cN feat/cN-frontend --allow "frontend/**,e2e/**"
   ```
2. `git merge --no-ff feat/cN-backend` y `git merge --no-ff feat/cN-frontend`.
3. `npm ci && node scripts/gate.mjs --e2e`. El E2E toma el candado `compose-e2e` (`scripts/lib/lock.mjs`), así que nunca corren dos stacks a la vez (bitácora #22).
4. Evidencia: `docker compose up -d --build --wait`, luego `npm run evidence -w e2e` y al final `docker compose down -v`.
5. Se marcan las tareas en `tasks.md`. Los supuestos nuevos van a `design.md § Supuestos` y las fallas a `docs/bitacora-fallas.md`.
6. **Archivo como último commit**:
   - `openspec archive <id> --yes`.
   - Se reemplaza el `Purpose` provisional de `openspec/specs/<cap>/spec.md` por el texto de `proposal.md`.
   - `openspec validate --all --strict --no-interactive`.
7. `node scripts/gate.mjs` y commit `chore(cN): integración, evidencia y archivo`.

## 5. PR, revisión y merge

1. Rebase sobre `main` si avanzó: `git rebase --onto origin/main <base-anterior> feat/cN`. Las ramas se fusionan con squash, por eso se rebasa con `--onto`.
2. `git push -u origin feat/cN` y `gh pr create`. El título sigue Conventional Commits (`feat(cN): …`). El cuerpo enlaza el cambio OpenSpec, lista los gates, embebe las capturas con permalinks `blob/<sha>/…?raw=true` y nombra las filas de bitácora.
3. **Gates de juicio**, ambos después de que corra el CI:
   - El subagente `revisor` (`/revisar-pr <n>`) publica un comentario con `VEREDICTO: APROBADO` en la **primera línea** y el SHA, y pone la etiqueta `revisor:aprobado`.
   - El subagente `seguridad` publica sus hallazgos y, si no hay ninguno alto ni crítico, pone `seguridad:ok`.
   - Cada push quita las etiquetas (`labels-reset.yml`) y obliga a revisar de nuevo.
4. Merge **solo** con `node scripts/merge-si-verde.mjs <n>` (ADR-004). El hook bloquea `gh pr merge` directo.
5. Se registra el fin de la fase con `node scripts/fase.mjs fin Cn` y se limpian los worktrees: `git worktree remove ../wt/cN-*`.

## Errores frecuentes (de la bitácora)

- Heredocs o `sed` desde Bash rompen las barras invertidas en regex: usar Write/Edit (#6).
- `mvnw.cmd` sin ruta absoluta falla en Windows (#16): usar `scripts/lib/run.mjs` o `scripts/mvn.mjs`.
- openapi-generator con URI `file:` genera clases distintas en Linux (#17): la ruta del contrato es relativa a `backend/`.
- CodeQL marca falsos positivos: se reescribe el código. No se descarta la alerta; descartar es una decisión humana (#11, #19).

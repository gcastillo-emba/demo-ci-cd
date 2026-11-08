## 1. Specs

- [x] 1.1 Acortar los 7 requisitos largos de `clients-matters` (delta `specs/clients-matters/spec.md`, `MODIFIED`); verificar que cada requisito conserva todos sus escenarios con el mismo nombre
- [x] 1.2 Acortar 3 requisitos de `description-assistant` y partir "Criterios de aceptación de evals" (delta `specs/description-assistant/spec.md`); verificar que los 5 escenarios de evals aparecen una sola vez y sin cambios
- [x] 1.3 Acortar los 2 requisitos largos de `hours-dashboard` (delta `specs/hours-dashboard/spec.md`, `MODIFIED`); misma verificación que 1.1
- [x] 1.4 Acortar 3 requisitos de `time-entries` y partir "Listado de registros con filtros y paginación" (delta `specs/time-entries/spec.md`); verificar que los 10 escenarios del listado aparecen una sola vez y sin cambios
- [x] 1.5 Validar el cambio con `openspec validate split-long-requirements --strict` en verde

## 2. Validador

- [x] 2.1 Subir `@fission-ai/openspec` a 1.15.0 en `package.json` y `package-lock.json`; verificar con `npm ci`
- [x] 2.2 Quitar de `.github/dependabot.yml` el `ignore` de `@fission-ai/openspec` `>= 1.14.1` y su comentario
- [x] 2.3 Archivar el cambio con `openspec archive split-long-requirements` y verificar `npm run spec:validate` en verde con 1.15.0 (4 de 4 specs)

## 3. Cierre

- [x] 3.1 Correr `npm run gate -- --rapido` y `npm run format:check` en verde
- [x] 3.2 Registrar el cierre de las filas 44 y 45 en `docs/bitacora-fallas.md`

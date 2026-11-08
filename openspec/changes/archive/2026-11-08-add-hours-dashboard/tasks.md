## 1. Contrato

- [x] 1.1 Agregar a `api/openapi.yaml` la ruta `GET /api/dashboard/summary` (security Bearer, query `from`/`to` `format: date` opcionales, respuestas `200`, `400`, `401` con `Problem`) y verificar con `npm run api:lint` en verde
- [x] 1.2 Agregar los esquemas `DashboardSummary`, `MatterHoursRow`, `LawyerHoursRow` y `HoursTotals` (horas `number`, `minimum: 0`, `multipleOf: 0.1`; `from`/`to` `format: date`) con ejemplos en español, y verificar con `npm run api:lint`
- [x] 1.3 Regenerar el cliente con `npm run api:generate` y verificar con `npm run api:check` en verde y `frontend/src/api/generated/schema.ts` versionado
- [x] 1.4 Verificar que el backend compila contra la interfaz generada nueva (`./mvnw -q compile` falla hasta que exista el controller y pasa después)

## 2. Backend

- [x] 2.1 Crear el value object de dominio `DateWindow` (`lastDays` con `Clock` en `America/La_Paz`, `of(from, to)` con reglas: ambos o ninguno, `from <= to`, máximo 92 días inclusive) y verificar con tests unitarios con `Clock.fixed` que cubren hoy-29/hoy, cambio de día UTC vs La Paz, 92 vs 93 días, `from > to` y parámetro faltante
- [x] 2.2 Registrar la excepción de validación de ventana en `ProblemCatalog` como `400 validation-error` con `errors[]` por campo y verificar con el test de ArchUnit que exige entrada en el catálogo
- [x] 2.3 Definir el puerto `in` `GetDashboardSummary` y el puerto `out` `DashboardReadRepository.aggregate(DateWindow, DashboardScope)` y verificar con ArchUnit (capas onion) en verde
- [x] 2.4 Implementar `DashboardQueryService` con alcance `ALL` para `PARTNER` y `OWN(lawyerId)` para `LAWYER`, totales calculados desde las filas, y verificar con tests de aplicación con fake en memoria (socio ve todo, abogado ve solo lo suyo, abogado sin horas → `byMatter` vacío, una única fila propia en `byLawyer` con ceros y totales en cero, totales = suma de filas)
- [x] 2.5 Implementar el adaptador de persistencia con sumas condicionales en décimas (`APPROVED`/`PENDING`, `REJECTED` excluido), orden por `matterCode` y `lawyerName`, y verificar con un IT Testcontainers que compara contra datos sembrados en el test
- [x] 2.6 Implementar el controller en `adapters.in.web` que implementa la interfaz generada, mapea décimas a decimal con una décima y error de formato de fecha a `validation-error`, y verificar con `ContractConformanceIT` en verde
- [x] 2.7 Revisar el plan de consulta con `EXPLAIN` en el IT y, solo si hay escaneo completo, agregar índice en una migración nueva; verificar que `./mvnw verify` sigue en verde
- [x] 2.8 Agregar o completar el seed `R__` de demo (perfil `demo`) con registros sintéticos `APPROVED`, `PENDING` y `REJECTED` con fechas relativas dentro de la ventana y verificar levantando compose con perfil `demo` y llamando al endpoint con el usuario socio
  - Nota (integración): el seed de C2 `R__seed_time_entries.sql` ya cumple (APPROVED/PENDING/REJECTED a 1–9 días); no se agregó un `R__` nuevo (design Supuesto 22). Verificado con compose `demo` por el E2E y la evidencia del socio (`01-tablero-socio.png`).
- [x] 2.9 Verificar `./mvnw verify` completo: unit + ArchUnit + IT + JaCoCo ≥ 80 % en `domain` + `application` + `spotless:check`

## 3. Frontend

- [x] 3.1 Agregar la ruta `/tablero` y el enlace "Tablero" en la navegación para `PARTNER` y `LAWYER`, y verificar con un test Vitest que el enlace aparece para ambos roles
- [x] 3.2 Implementar la página "Tablero" que llama al endpoint sin `from`/`to` con el cliente generado y muestra el período ("Últimos 30 días: dd/mm/aaaa – dd/mm/aaaa") y los totales "Aprobadas: X h" / "Pendientes: Y h", y verificar con Vitest con respuesta simulada
- [x] 3.3 Implementar el componente `HoursTable` (`<caption>`, `<th scope="col">`, `<th scope="row">`) para "Horas por asunto" y "Horas por abogado", y verificar con Vitest + Testing Library consultando por rol `table` y nombre accesible
- [x] 3.4 Implementar el componente `HoursBar` con segmentos proporcionales, patrón rayado para pendientes y texto "Aprobadas: X h · Pendientes: Y h" (formato `es-BO`, "0 h" en ceros), y verificar con Vitest los anchos relativos y los textos
- [x] 3.5 Implementar los estados de carga, vacío ("No hay horas registradas en los últimos 30 días." cuando los totales son cero, conservando la fila propia en cero del abogado), error (`role="alert"`) y redirección al login con `401`, y verificar con un test Vitest por estado
- [x] 3.6 Verificar `npm run frontend:lint`, `prettier --check`, typecheck y Vitest en verde

## 4. QA y evidencia

- [x] 4.1 Escribir el IT de alcance por rol (socio ve tres abogados y cuatro asuntos; abogada no ve nombres ni asuntos ajenos ni puede ampliar alcance con `lawyerId`) y verificar que pasa en `./mvnw verify`
- [x] 4.2 Escribir ITs HTTP para cada escenario de error (`401` sin token y con token expirado; `400 validation-error` por rango > 92 días, `from > to`, `from` faltante, `to` faltante, formato inválido y fecha inexistente) comprobando `code`, `type`, `errors[].field` y `traceId`, y verificar que pasan
- [x] 4.3 Escribir ITs para ventana por defecto con `Clock` fijo (límites hoy-29/hoy/hoy-30), exclusión de `REJECTED`, totales consistentes y período vacío, y verificar que pasan
- [x] 4.4 Agregar trazabilidad escenario → test (nombre o comentario con el título del `#### Scenario`) partiendo de la tabla brief → requisito → escenario de `proposal.md`, y verificar que cada escenario de `specs/hours-dashboard/spec.md` tiene al menos un test
- [x] 4.5 Escribir el E2E Playwright del Tablero (socio y abogado con seed de demo: título, período, dos tablas con `caption`, textos "Aprobadas"/"Pendientes", abogado con una sola fila) y verificar con `npm run e2e` sobre compose
- [x] 4.6 Ejecutar una revisión de accesibilidad automática (axe en el E2E) sin violaciones serias en "Tablero" y verificar en el reporte de Playwright
  - Nota (integración): `e2e/tests/tablero.a11y.spec.ts` con `@axe-core/playwright` 4.14.0 (WCAG 2.x A/AA, socio y abogado, sin logins nuevos); el resultado de axe se adjunta al reporte. Verde en el gate `--e2e`.
- [x] 4.7 Guardar capturas (socio, abogado, vacío, vista en escala de grises) en `docs/evidence/add-hours-dashboard/` y verificar que están enlazadas en el PR
  - Nota (integración): `01-tablero-socio.png`, `02-tablero-abogado.png`, `03-tablero-vacio.png`, `04-tablero-escala-grises.png` generadas solo con el spec de C3; el enlace se agrega al abrir el PR `feat(c3)`.
- [x] 4.8 Ejecutar `npm run gate -- --e2e` en local y verificar que queda en verde

## 5. Cierre

- [x] 5.1 Actualizar `docs/glosario.md` (términos del tablero: ventana, aprobadas/pendientes agregadas, y la decisión del Supuesto 1: el `byLawyer` de un abogado sin horas tiene su única fila con ceros) y nuevos supuestos en `design.md` si aparecieron, y verificar con revisión del diff
- [x] 5.2 Registrar en `docs/bitacora-fallas.md` las fallas del ciclo y verificar que cada gate fallido tiene fila
- [x] 5.3 Verificar `npm run spec:validate` (`openspec validate --all --strict --no-interactive`) en verde y todas las tareas marcadas `[x]`
- [x] 5.4 Ejecutar `openspec archive add-hours-dashboard` como último commit del PR `feat(c3)`; en ese mismo commit reemplazar el `## Purpose` provisional de `openspec/specs/hours-dashboard/spec.md` por el texto de "Propósito de la capacidad" de `proposal.md`, y verificar que el archivo existe con ese Purpose y que la validación estricta sigue en verde
- [ ] 5.5 Obtener `ci-ok` en verde y las etiquetas `revisor:aprobado` y `seguridad:ok` posteriores al commit de archivo (último commit), verificado por `scripts/merge-si-verde.mjs`
  - Nota (integración): abierta a propósito; depende del PR y de la revisión humana posteriores al commit de archivo.

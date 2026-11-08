## 1. Contrato

- [ ] 1.1 Agregar a `api/openapi.yaml` los esquemas `Client`, `ClientCreate`, `Matter`, `MatterCreate`, `MatterStatus`, `ClientPage` y `MatterPage` (design D1); verificar con `npm run api:lint` sin errores.
- [ ] 1.2 Agregar los 7 endpoints (`GET/POST /api/clients`, `GET /api/clients/{clientId}`, `GET/POST /api/matters`, `GET /api/matters/{matterId}`, `POST /api/matters/{matterId}/close`) con parámetros `page`, `size` (min/max), `q`, `clientId`, `status`, `security: bearerAuth` y respuestas 400/401/403/404/409 `application/problem+json`; verificar con `npm run api:lint` y revisando que cada `code` de la spec aparezca en la descripción de su respuesta.
- [ ] 1.3 Regenerar el cliente (`npm run api:generate`) y commitear `frontend/src/api/generated/schema.ts`; verificar con `npm run api:check` en verde.
- [ ] 1.4 Verificar que el contrato genera las interfaces del backend con `./mvnw -q generate-sources` (las interfaces `ClientsApi` y `MattersApi` se generan sin errores; su implementación llega en 2.6).

## 2. Backend

- [ ] 2.1 Migración `V2__clients_matters.sql` con `clients`, `matters`, `matter_code_seq`, índice único parcial de `tax_id`, checks de `status`/`closed_at` y `version` (design D2); verificar con un IT Testcontainers que Flyway migra desde cero y que el índice parcial permite varios `tax_id` nulos.
- [ ] 2.2 Dominio: `Client`, `Matter` (con `close(Instant)`), `MatterCode`, `MatterStatus` y excepciones `ClientNotFound`, `MatterNotFound`, `ClientTaxIdTaken`, `MatterAlreadyClosed`; verificar con tests unitarios de `MatterCode` (formato, ceros, límite 9999) y de `Matter.close` (OPEN→CLOSED, CLOSED→excepción).
- [ ] 2.3 Entradas en `ProblemCatalog` para `not-found`, `client-tax-id-taken`, `matter-already-closed` (design D7); verificar con la regla ArchUnit "toda `DomainException` tiene entrada en `ProblemCatalog`".
- [ ] 2.4 Puertos y servicios `application`: crear/listar/obtener clientes, crear/listar/obtener/cerrar asuntos, autorización por rol con `CurrentUser` (design D6) y generación de código con `Clock` (design D3); verificar con tests unitarios por escenario de la spec (incluido 403 por cada escritura de `LAWYER`) y JaCoCo ≥ 80 % en `domain` + `application`.
- [ ] 2.5 Adaptador de persistencia: entidades JPA, repositorios con `Specification` para filtros `q`/`clientId`/`status`, upsert de `matter_code_seq`, traducción de la violación de `ux_clients_tax_id` (design D4, D8); verificar con ITs Testcontainers de filtros, orden, paginación y altas concurrentes de asuntos (códigos distintos).
- [ ] 2.6 Controllers web que implementan las interfaces generadas, `Location` en 201, mapeo a Problem Details, `405 method-not-allowed` y reglas por URL de `PARTNER` para las escrituras, que garantizan la precedencia `401 > 403 > 400 > 404 > 409` (design D6, S11); verificar con `ContractConformanceIT` (endpoints iguales al contrato) y con ITs MockMvc de 400/401/403/404/405/409 que comprueban `code`, `type`, `errors[]` y `traceId`, incluidos los escenarios de precedencia (Abogado con cuerpo inválido → 403, Abogado cierra un asunto inexistente → 403, Socio con cuerpo inválido y cliente inexistente → 400).
- [ ] 2.7 Seed `R__seed_clients_matters.sql` (perfil `demo`, idempotente, 25 clientes y ~30 asuntos sintéticos); verificar arrancando dos veces con perfil `demo` sin errores ni duplicados.
- [ ] 2.8 Verificar el backend completo con `./mvnw verify` (unit + ArchUnit + Testcontainers + JaCoCo + `spotless:check`) en verde.

## 3. Frontend

- [ ] 3.1 Componentes reutilizables `DataTable`, `Pagination`, `SearchInput` y `ConfirmDialog` y hook `usePagedQuery` (design D9); verificar con tests Vitest de paginación, _debounce_ y foco del diálogo.
- [ ] 3.2 Pantalla **Clientes** (`/clientes`): lista, búsqueda por nombre, paginación, mensaje `No se encontraron clientes`; verificar con tests Vitest de los escenarios "Buscar un cliente", "Navegar a la página siguiente" y "Búsqueda sin resultados".
- [ ] 3.3 Pantalla **Asuntos** (`/asuntos`): lista con estado `Abierto`/`Cerrado`, filtros cliente/estado/búsqueda y paginación; verificar con tests Vitest de los escenarios de filtros.
- [ ] 3.4 Formularios **Nuevo cliente** y **Nuevo asunto** solo para `PARTNER`, validación local, errores por campo y mensaje de `client-tax-id-taken`; verificar con tests Vitest por rol (Socio ve, Abogado no ve) y de errores de validación y 409.
- [ ] 3.5 Acción **Cerrar asunto** con `ConfirmDialog`, solo para `PARTNER` y asuntos `Abierto`, manejo de `409 matter-already-closed`; verificar con tests Vitest de confirmar, cancelar (sin petición), 409 y ausencia para Abogado y para asuntos cerrados.
- [ ] 3.6 Verificar el frontend con `npm run frontend:lint`, `prettier --check`, `npm run frontend:typecheck` y `npm run frontend:test` en verde.

## 4. QA y evidencia

- [ ] 4.1 Matriz de trazabilidad escenario → test (nombre o comentario `// Scenario: <nombre>`) para todos los escenarios de `specs/clients-matters/spec.md`, partiendo de la tabla de `design.md` § Trazabilidad; verificar que ningún escenario queda sin test. El caso "cerrar con registros `PENDING`" no se puede ejecutar en C1 porque no existe `time_entries`: su IT lo agrega C2 (`add-time-entries`), según design S1.
- [ ] 4.2 E2E Playwright: Socio crea cliente, crea asunto (ve el código `AS-YYYY-NNNN`), filtra, pagina y cierra con confirmación; verificar con `npm run e2e` en verde sobre compose.
- [ ] 4.3 E2E Playwright: Abogado ve Clientes y Asuntos sin botones de alta ni de cierre, y la API responde 403 a un `POST` directo; verificar con `npm run e2e` en verde.
- [ ] 4.4 Capturas de las pantallas Clientes, Asuntos, formulario de alta y diálogo de cierre en `docs/evidence/add-clients-matters/`; verificar que los archivos existen y quedan enlazados en el PR.
- [ ] 4.5 Ejecutar `npm run gate -- --e2e` en local; verificar que todos los gates quedan en verde y registrar cualquier falla en `docs/bitacora-fallas.md`.

## 5. Cierre

- [ ] 5.1 Actualizar `docs/glosario.md` si surgieron términos nuevos (p. ej. `taxId`, `closedAt`), agregar `method-not-allowed` (405) y la precedencia `401 > 403 > 400 > 404 > 409` a las convenciones de error compartidas de `docs/plan-maestro.md`, y anotar en `design.md` § Supuestos todo supuesto nuevo; verificar por revisión del diff.
- [ ] 5.2 Obtener `ci-ok` en verde en el PR `feat(c1)` y las etiquetas `revisor:aprobado` y `seguridad:ok` posteriores al último commit; verificar con `gh pr checks` y `gh pr view --json labels`.
- [ ] 5.3 Marcar todas las tareas `[x]` y ejecutar `openspec validate add-clients-matters --strict --no-interactive`; verificar que pasa.
- [ ] 5.4 Como último commit del PR de la capacidad, ejecutar `openspec archive add-clients-matters` (crea `openspec/specs/clients-matters/spec.md`) y, en el mismo commit, reemplazar su `## Purpose` provisional por el texto de `proposal.md` § Propósito de la capacidad; verificar con `openspec validate --all --strict --no-interactive` en verde y que el cambio aparece en `openspec/changes/archive/`.

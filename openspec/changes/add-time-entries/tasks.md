## 1. Contrato

- [ ] 1.1 Agregar a `api/openapi.yaml` los endpoints `GET`/`POST /api/time-entries`, `PUT /api/time-entries/{id}`, `POST /api/time-entries/{id}/approve` y `POST /api/time-entries/{id}/reject` con sus respuestas 200/201/400/401/403/404/409/422 en `application/problem+json`; verificar con `npm run api:lint` en verde
- [ ] 1.2 Definir los esquemas `TimeEntry`, `TimeEntryCreate`, `TimeEntryUpdate` (con `version`), `RejectRequest`, `TimeEntryPage` y `DailyHoursExceededProblem` (`limitHours`, `alreadyLoggedHours`); verificar con `npm run api:lint` y revisión de que los nombres coinciden con `docs/glosario.md`
- [ ] 1.3 Documentar en el contrato los parámetros `matterId`, `lawyerId`, `status`, `from`, `to`, `page`, `size` (máx. 100) y `sort` (enum cerrado `workDate,desc` por defecto | `createdAt,asc`), más el campo `editableUntil` en `TimeEntry`; verificar con `npm run api:lint`
- [ ] 1.4 Regenerar el cliente con `npm run api:generate` y commitear `frontend/src/api/generated/schema.ts`; verificar con `npm run api:check` en verde

## 2. Backend

- [ ] 2.1 Migración `V3__time_entries.sql` (décimas 1..240, `status`, `version`, `rejection_reason`, índice `(lawyer_id, work_date)`); verificar con un IT de Testcontainers que Flyway migra y que los CHECK rechazan valores inválidos
- [ ] 2.2 Dominio `TimeEntry`, `TimeEntryStatus`, value object `Hours.of(BigDecimal)` que recibe el valor crudo del adaptador web y lanza `hours-invalid-increment` / `hours-out-of-range` (design D1), cálculo de `editableUntil` y reglas (incremento, rango, fecha futura, ventana de 7 días, estados terminales, límite diario); verificar con unit tests con `Clock.fixed` en `America/La_Paz`, incluido el borde 2026-11-08T02:30Z
- [ ] 2.3 Excepciones de dominio y entradas en `ProblemCatalog` para los 10 slugs nuevos (design D6); verificar con la regla ArchUnit de catálogo y un test de mapeo slug → status
- [ ] 2.4 Caso de uso de alta con autorización por rol (403 `forbidden` para `PARTNER`), asunto `OPEN` y límite diario bajo `DailyHoursLock`; verificar con tests de aplicación con fakes en memoria por cada escenario del requisito de alta
- [ ] 2.5 Caso de uso de edición (propiedad, `PENDING`, ventana, `version`, horas previas excluidas, bloqueo de dos fechas); verificar con tests de aplicación por cada escenario de edición
- [ ] 2.6 Casos de uso de aprobación y rechazo (rol, `PENDING`, motivo 1..500 → `rejection-reason-required`); verificar con tests de aplicación de aprobar, rechazar y estados terminales
- [ ] 2.7 Caso de uso de listado con filtros, alcance por rol (`lawyerId` ignorado para `LAWYER`), paginación, orden (`sort` por defecto y `createdAt,asc`; valor no admitido → 400) y `editableUntil`; verificar con tests de aplicación y un IT de paginación y orden
- [ ] 2.8 Adaptador de persistencia (JPA, `@Version`, `pg_advisory_xact_lock`) y controller que implementa la interfaz generada; verificar con `./mvnw verify` (compilación contra interfaces + `ContractConformanceIT`)
- [ ] 2.9 IT de concurrencia: dos altas paralelas de 13 h el mismo día → una 201 y una 422 `daily-hours-exceeded`; alta de 10 h en paralelo con edición de 10 h → 14 h el mismo día → una exitosa y una 422; verificar con `./mvnw verify` en verde
- [ ] 2.10 IT HTTP de Problem Details para cada slug de C2 (status, `code`, `type`, `detail` en español, `traceId`, extensión del límite diario); verificar con `./mvnw verify`
- [ ] 2.11 Seed `R__demo_time_entries.sql` idempotente con fechas relativas y perfil `demo`; verificar arrancando compose dos veces sin errores ni duplicados
- [ ] 2.12 IT de interacción con C1: cerrar un asunto con registros `PENDING` → `200` y `status` `CLOSED`, los registros siguen `PENDING` y el socio puede aprobarlos (design Supuesto 13); verificar con `./mvnw verify` en verde

## 3. Frontend

- [ ] 3.1 Mapa `code → mensaje en español` y utilidad de parseo de horas con coma o punto decimal; verificar con Vitest (cada slug de C2 tiene mensaje; "1,5" y "1.5" → 1.5; "1,25" se rechaza)
- [ ] 3.2 Pantalla "Registrar horas" (asuntos `OPEN`, fecha por defecto hoy en La Paz, errores junto al campo, conserva datos ante error); verificar con Vitest + Testing Library
- [ ] 3.3 Pantalla "Mis registros" paginada con estado, motivo de rechazo y "Editar" solo si `PENDING` y `editableUntil` ≥ hoy; manejo de 409 con recarga; verificar con Vitest
- [ ] 3.4 Pantalla "Aprobaciones" con aprobar, diálogo de rechazo con motivo obligatorio (botón deshabilitado si vacío, contador 0/500) y recarga ante 409; verificar con Vitest
- [ ] 3.5 Navegación y guard por rol (abogado no ve "Aprobaciones"; socio no ve "Registrar horas" ni "Mis registros"); verificar con Vitest y `npm run frontend:lint`/typecheck en verde

## 4. QA y evidencia

- [ ] 4.1 Matriz de trazabilidad escenario → test en `docs/evidence/add-time-entries/trazabilidad.md`; verificar que cada `#### Scenario` de la spec tiene al menos un test
- [ ] 4.2 E2E Playwright: abogado registra con coma decimal, edita un registro pendiente y recibe el error de límite diario en español; verificar con `npm run e2e` en verde
- [ ] 4.3 E2E Playwright: socio aprueba y rechaza con motivo desde "Aprobaciones"; abogado ve el motivo en "Mis registros"; verificar con `npm run e2e` en verde
- [ ] 4.4 Capturas de las tres pantallas y de los errores en `docs/evidence/add-time-entries/`; verificar que están enlazadas en el PR
- [ ] 4.5 Arnés completo; verificar con `npm run gate -- --e2e` en verde (JaCoCo ≥ 80 % en `domain` + `application`) y `ci-ok` en verde en el PR

## 5. Cierre

- [ ] 5.1 Actualizar `docs/glosario.md` y la sección Supuestos de `design.md` con lo surgido en la implementación; verificar con `npm run spec:validate` en verde
- [ ] 5.2 Registrar las fallas del ciclo en `docs/bitacora-fallas.md`; verificar que cada gate fallido tiene su fila
- [ ] 5.3 Obtener `revisor:aprobado` y `seguridad:ok` posteriores al último commit; verificar con `gh pr view --json labels`
- [ ] 5.4 Ejecutar `openspec archive add-time-entries` como último commit del PR de la feature; verificar que `openspec/specs/time-entries/spec.md` existe y que `openspec validate --all --strict --no-interactive` pasa

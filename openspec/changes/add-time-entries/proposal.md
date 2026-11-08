## Why

La firma necesita que cada abogado registre las horas trabajadas en los asuntos abiertos y que un socio las apruebe o rechace antes de que cuenten como trabajo validado. Es la capacidad C2 del POC "Asuntos & Horas" y el riesgo principal del plan maestro: concentra las reglas de negocio con más bordes (límite diario de 24 h, décimas de hora, fecha futura en zona `America/La_Paz`, ventana de edición de 7 días, estados terminales y concurrencia).

## What Changes

- Nuevo recurso `TimeEntry` (registro de horas) con estados `PENDING`, `APPROVED` y `REJECTED`; `APPROVED` y `REJECTED` son terminales.
- `POST /api/time-entries`: un abogado (`LAWYER`) registra horas en un asunto `OPEN` con `workDate`, `hours` y `description` (1..2000). El registro nace `PENDING`. Un socio (`PARTNER`) no puede registrar horas (403 `forbidden`).
- Reglas de negocio con su slug estable de Problem Details:
  - horas en incrementos de 0,1 (422 `hours-invalid-increment`), mayores que 0 y hasta 24 (422 `hours-out-of-range`);
  - suma diaria del abogado (`PENDING` + `APPROVED`, incluido el nuevo) ≤ 24 h (422 `daily-hours-exceeded` con `limitHours` y `alreadyLoggedHours`; `REJECTED` no cuenta);
  - `workDate` no posterior a "hoy" en `America/La_Paz` (422 `future-date`);
  - asunto `CLOSED` (422 `matter-closed`).
- `PUT /api/time-entries/{id}` con `version`: solo el autor (403 `not-entry-owner`), solo en `PENDING` (409 `entry-not-pending`), solo dentro de 7 días calendario desde la creación (422 `edit-window-expired`), con las mismas reglas de horas, fecha y asunto (las horas previas del propio registro no cuentan en la suma diaria); versión obsoleta → 409 `concurrent-modification`.
- `POST /api/time-entries/{id}/approve` y `POST /api/time-entries/{id}/reject` (con `reason` 1..500, si no 422 `rejection-reason-required`): solo `PARTNER` (403 `forbidden` para `LAWYER`), solo sobre `PENDING` (409 `entry-not-pending`).
- `GET /api/time-entries` paginado (`page`, `size`, sobre `{items, page, size, totalItems, totalPages}`) con filtros `matterId`, `lawyerId` (solo socio), `status`, `from`, `to`. El abogado siempre ve solo lo suyo.
- Dos altas concurrentes que juntas superan 24 h para el mismo abogado y día no pueden tener éxito ambas.
- UI: formulario "Registrar horas" (acepta coma decimal), lista "Mis registros" con edición de los registros editables, y cola "Aprobaciones" del socio con aprobar y diálogo de rechazo con motivo obligatorio. Mensajes de error claros en español.

## Capabilities

### New Capabilities

- `time-entries`: registro, edición, aprobación/rechazo y consulta de registros de horas, con sus reglas de negocio, autorización por rol y propiedad, concurrencia y las pantallas "Registrar horas", "Mis registros" y "Aprobaciones".

### Modified Capabilities

<!-- Ninguna: C1 (`add-clients-matters`) aporta `Matter` y su estado; este cambio solo lo consume. -->

## Impact

- **Contrato** `api/openapi.yaml`: tag `time-entries`, esquemas `TimeEntry`, `TimeEntryCreate`, `TimeEntryUpdate`, `RejectRequest`, `TimeEntryPage`, extensión de `Problem` para `daily-hours-exceeded`; cliente generado `frontend/src/api/generated/schema.ts`.
- **Backend**: dominio `TimeEntry`/`TimeEntryStatus`/horas en décimas, casos de uso en `application`, persistencia con migración Flyway `V3` (`time_entries`), nuevas entradas en `ProblemCatalog`, seed `R__` de demo.
- **Frontend**: rutas y pantallas "Registrar horas", "Mis registros", "Aprobaciones"; navegación por rol.
- **QA**: unit con `Clock.fixed` en `America/La_Paz`, IT con Testcontainers (incluido el de concurrencia), E2E de edición, límite diario y aprobar/rechazar; evidencia en `docs/evidence/add-time-entries/`.
- **Dependencias**: requiere el walking skeleton (F3: login, `/api/me`, Problem Details, `Clock`) y C1 (`add-clients-matters`: asuntos y su estado `OPEN`/`CLOSED`).

## Propósito de la capacidad

Texto del `## Purpose` de `openspec/specs/time-entries/spec.md`. La spec delta solo contiene operaciones. Al archivar, `openspec archive` crea la spec principal con un Purpose provisional, y en el mismo commit se reemplaza por este texto (ver tarea 5.4):

> Permitir que los abogados registren las horas trabajadas en asuntos abiertos y que los socios las aprueben o rechacen, aplicando las reglas de negocio de la firma (décimas de hora, límite diario de 24 h, fecha no futura en `America/La_Paz`, ventana de edición de 7 días y estados terminales).

La trazabilidad regla del brief → requisito → escenario está en `design.md` § Trazabilidad.

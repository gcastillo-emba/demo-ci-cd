## Why

El registro de horas (C2) y el tablero (C3) necesitan un catálogo confiable de clientes y asuntos al que imputar el trabajo, y hoy el sistema solo tiene el esqueleto de autenticación. C1 es la primera capacidad de negocio: fija el maestro de datos, el modelo de autorización por rol (Socio escribe, Abogado lee) y las convenciones de API (paginación, Problem Details) que reutilizan C2–C4.

## What Changes

- Nuevo recurso **Cliente** (`Client`): el Socio crea clientes (nombre obligatorio de 1 a 200 caracteres; NIT/identificación tributaria `taxId` opcional y único) y cualquier usuario autenticado los lista (filtro `q` por nombre, paginación) y los consulta por id.
- Nuevo recurso **Asunto** (`Matter`): el Socio crea asuntos para un cliente existente (título de 1 a 200 caracteres, descripción opcional de hasta 2000). El servidor genera el código `AS-YYYY-NNNN` y el estado inicial `OPEN`. Cualquier usuario autenticado lista asuntos (filtros `clientId`, `status` `OPEN|CLOSED`, `q` sobre título o código; paginación) y los consulta por id.
- Cierre de asunto: `POST /api/matters/{matterId}/close` pasa el asunto a `CLOSED` y registra `closedAt`. Cerrar un asunto ya cerrado responde 409 `matter-already-closed`. No existe forma de reabrir un asunto.
- Autorización: el Abogado (`LAWYER`) solo puede leer; cualquier escritura responde 403 `forbidden`. Sin token, 401 `unauthenticated`.
- Errores RFC 9457 nuevos o reutilizados: `validation-error` (400), `unauthenticated` (401), `forbidden` (403), `not-found` (404), `method-not-allowed` (405, slug técnico del framework), `client-tax-id-taken` (409), `matter-already-closed` (409) e `internal-error` (500, respaldo genérico compartido por toda la API: sin trazas, clases de excepción ni SQL en la respuesta). Precedencia: `401 > 403 > 400 > 404 > 409`.
- Endpoints en el contrato `api/openapi.yaml`: `GET/POST /api/clients`, `GET /api/clients/{clientId}`, `GET/POST /api/matters`, `GET /api/matters/{matterId}`, `POST /api/matters/{matterId}/close`.
- Interfaz en español: pantallas **Clientes** y **Asuntos** con filtros, paginación, formularios de alta (solo Socio; ocultos para Abogado) y acción **Cerrar asunto** con confirmación.

## Capabilities

### New Capabilities

- `clients-matters`: gestión de clientes y asuntos (alta, listado filtrado y paginado, consulta por id, cierre irreversible de asuntos), autorización por rol y pantallas Clientes y Asuntos.

### Modified Capabilities

<!-- Ninguna: no hay specs vigentes en openspec/specs/. -->

## Impact

- **Contrato**: `api/openapi.yaml` gana los esquemas `Client`, `ClientCreate`, `Matter`, `MatterCreate`, `MatterStatus`, `ClientPage`, `MatterPage` y los 7 endpoints; se regenera `frontend/src/api/generated/schema.ts`.
- **Backend**: migración Flyway `V2` (tablas `clients` y `matters`), dominio (`Client`, `Matter`, `MatterCode`, `MatterStatus`), casos de uso con autorización por rol en `application.service`, controllers que implementan las interfaces generadas, entradas nuevas en `ProblemCatalog`, seed `R__` de demo.
- **Frontend**: rutas y pantallas Clientes y Asuntos, componentes de filtro/paginación reutilizables por C2–C3.
- **QA**: tests unitarios, ArchUnit, IT con Testcontainers, `ContractConformanceIT`, Vitest y E2E Playwright con evidencia en `docs/evidence/add-clients-matters/`.
- **Dependencias aguas abajo**: C2 (registros de horas) depende de `Matter` y de su estado `OPEN`/`CLOSED`.

## Propósito de la capacidad

Este es el texto del `## Purpose` de `openspec/specs/clients-matters/spec.md`. La spec delta solo contiene operaciones. Al archivar, `openspec archive` crea la spec principal con un Purpose provisional, y en el mismo commit se reemplaza por este texto (ver tarea 5.4):

> Gestionar el maestro de clientes y asuntos de la firma: el Socio da de alta clientes y asuntos y cierra asuntos de forma irreversible; todo usuario autenticado puede consultarlos con filtros y paginación, a través de la API `/api` y de las pantallas Clientes y Asuntos.

La trazabilidad regla del brief → requisito → escenario está en `design.md` § Trazabilidad.

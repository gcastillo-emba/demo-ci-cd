## Why

Socios y abogados necesitan ver de un vistazo cuántas horas se han cargado en el último mes y cuántas siguen pendientes de revisión, por asunto y por abogado, sin exportar datos ni revisar registro por registro. Es la capacidad C3 del POC "Asuntos & Horas" y depende de los registros de horas de C2 (`add-time-entries`) y de los asuntos de C1 (`add-clients-matters`).

## What Changes

- Nuevo endpoint `GET /api/dashboard/summary` (Bearer JWT) que devuelve las horas agregadas de una ventana de fechas:
  - Ventana por defecto: últimos 30 días, `[hoy-29, hoy]` inclusive, con "hoy" en la zona de la firma `America/La_Paz`.
  - Parámetros opcionales `from` / `to` (fechas ISO) con rango máximo de 92 días; fuera de eso responde `400 validation-error`.
  - Respuesta con `byMatter[]` (`matterId`, `matterCode`, `matterTitle`, `approvedHours`, `pendingHours`), `byLawyer[]` (`lawyerId`, `lawyerName`, `approvedHours`, `pendingHours`) y `totals` (`approvedHours`, `pendingHours`), más la ventana efectiva (`from`, `to`).
  - Solo cuentan registros `APPROVED` y `PENDING`; `REJECTED` queda excluido.
  - Alcance por rol: `PARTNER` ve a todos los abogados y asuntos; `LAWYER` ve solo sus propios registros.
  - Período sin horas: listas vacías y totales en cero.
- Nueva pantalla **"Tablero"** en el frontend, con ventana fija de 30 días, dos tablas accesibles (`caption`, `th scope`) y barras CSS que distinguen aprobadas y pendientes por texto, no solo por color.
- Errores en Problem Details (RFC 9457) según la política compartida (`400 validation-error`, `401 unauthenticated`).

## Capabilities

### New Capabilities

- `hours-dashboard`: resumen agregado de horas aprobadas y pendientes por asunto y por abogado en una ventana de fechas, con alcance según el rol, y su pantalla "Tablero".

### Modified Capabilities

Ninguna.

## Impact

- **Contrato** `api/openapi.yaml`: nueva ruta `GET /api/dashboard/summary`, esquemas `DashboardSummary`, `MatterHoursRow`, `LawyerHoursRow`, `HoursTotals`; reutiliza `Problem`.
- **Backend**: nuevo caso de uso de consulta en `application` (con autorización por rol), consulta agregada de solo lectura en `adapters.out.persistence` sobre `time_entries`, `matters` y `users`; controller en `adapters.in.web`. Sin migraciones nuevas (lee tablas de C1/C2); seed `R__` de demo con registros en la ventana.
- **Frontend**: ruta y entrada de navegación "Tablero", componentes de tabla con barras; cliente regenerado (`npm run api:generate`).
- **QA**: IT de alcance por rol, pruebas de ventana con `Clock.fixed`, Vitest de accesibilidad y E2E con captura en `docs/evidence/add-hours-dashboard/`.
- **Dependencias**: requiere C1 y C2 fusionados. Sin dependencias externas nuevas.

## Propósito de la capacidad

Texto del `## Purpose` de `openspec/specs/hours-dashboard/spec.md` (la spec delta solo contiene operaciones; al archivar, `openspec archive` crea la spec principal con un Purpose provisional que se reemplaza por este texto en el mismo commit, ver tarea 5.4):

> Ofrecer a socios y abogados un resumen agregado de las horas aprobadas y pendientes por asunto y por abogado en una ventana de fechas (por defecto, los últimos 30 días en la zona horaria de la firma), con alcance según el rol, a través de `GET /api/dashboard/summary` y de la pantalla "Tablero".

## Decisiones pendientes de confirmación

- **Abogado sin horas en la ventana (Supuesto 1 de `design.md`).** El brief dice que el `byLawyer` de un `LAWYER` tiene "exactamente una fila: él mismo", y también que un período sin horas devuelve listas vacías. La spec resuelve el conflicto así: a lo sumo una fila, exactamente una si el abogado tiene horas contadas, y lista vacía si no las tiene. Es una reinterpretación del brief: el dueño del plan debe confirmarla antes de empezar la implementación (tarea 1.0), y la decisión se registra en `docs/glosario.md` (tarea 5.1) para que no se vuelva a discutir.

## Trazabilidad brief → requisito → escenario

| Regla del brief                                                                           | Requisito (`specs/hours-dashboard/spec.md`)                                                                          | Escenario(s)                                                                                                      |
| ----------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Endpoint autenticado con Bearer JWT; `401 unauthenticated`                                | Autenticación del resumen del tablero                                                                                | Petición sin token; Token expirado                                                                                |
| Ventana por defecto de 30 días `[hoy-29, hoy]` en `America/La_Paz`                        | Ventana por defecto de 30 días en la zona de la firma                                                                | Límites de la ventana por defecto; "Hoy" se calcula en la zona de la firma y no en UTC                            |
| Parámetros opcionales `from` / `to`                                                       | Ventana personalizada con from y to                                                                                  | Rango válido de 92 días; Rango de un solo día                                                                     |
| Rango máximo de 92 días → `400 validation-error`                                          | Rango máximo de 92 días                                                                                              | Rango mayor a 92 días                                                                                             |
| Validación de la ventana (política de errores `400 validation-error`)                     | Orden de las fechas de la ventana; Parámetros de la ventana enviados juntos; Formato ISO de las fechas de la ventana | from posterior a to; Solo se envía from; Solo se envía to; Fecha con formato inválido; Fecha inexistente          |
| `byMatter[]` con `matterId`, `matterCode`, `matterTitle`, `approvedHours`, `pendingHours` | Horas agregadas por asunto                                                                                           | Suma por asunto separando aprobadas y pendientes                                                                  |
| `byLawyer[]` con `lawyerId`, `lawyerName`, `approvedHours`, `pendingHours`                | Horas agregadas por abogado                                                                                          | Suma por abogado                                                                                                  |
| `totals` con `approvedHours`, `pendingHours`                                              | Totales del período                                                                                                  | Totales consistentes con las filas                                                                                |
| Solo cuentan `APPROVED` y `PENDING`; `REJECTED` excluido                                  | Exclusión de registros rechazados                                                                                    | Registro rechazado no suma                                                                                        |
| `PARTNER` ve todos los abogados y asuntos                                                 | Alcance del socio                                                                                                    | El socio ve a todos los abogados                                                                                  |
| `LAWYER` ve solo lo suyo; `byLawyer` con una fila (ver Decisiones pendientes)             | Alcance del abogado                                                                                                  | El abogado solo ve sus horas; El abogado no puede ampliar su alcance con parámetros                               |
| Período sin horas: listas vacías y totales en cero                                        | Período sin horas                                                                                                    | Ventana sin registros para el socio; Abogado sin horas propias en la ventana                                      |
| Pantalla "Tablero" con ventana fija de 30 días                                            | Pantalla Tablero                                                                                                     | El socio abre el Tablero; El abogado ve solo su fila; Sesión expirada en el Tablero; Error del servidor al cargar |
| Tablas accesibles con `caption` y `th scope`                                              | Tablas accesibles por asunto y por abogado                                                                           | Estructura accesible de la tabla por asunto; Estructura accesible de la tabla por abogado                         |
| Barras CSS que distinguen aprobadas y pendientes con texto, no solo color                 | Barras de horas distinguibles sin depender del color                                                                 | Diferencia entre aprobadas y pendientes visible en escala de grises; Categoría en cero                            |
| Período vacío visible en la UI                                                            | Estado vacío del Tablero                                                                                             | Tablero sin datos                                                                                                 |

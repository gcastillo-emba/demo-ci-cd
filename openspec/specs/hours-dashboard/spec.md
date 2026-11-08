# hours-dashboard Specification

## Purpose

Ofrecer a socios y abogados un resumen agregado de las horas aprobadas y pendientes por asunto y por abogado en una ventana de fechas (por defecto, los últimos 30 días en la zona horaria de la firma), con alcance según el rol, a través de `GET /api/dashboard/summary` y de la pantalla "Tablero".

## Requirements

### Requirement: Autenticación del resumen del tablero

El sistema SHALL (DEBE) exigir un token de acceso Bearer válido en `GET /api/dashboard/summary`. El sistema SHALL NOT (NO DEBE) devolver datos agregados a una petición sin token, con token inválido o con token expirado; en esos casos SHALL (DEBE) responder `401` con `application/problem+json` y `code` igual a `unauthenticated`.

#### Scenario: Petición sin token

- **GIVEN** (Dado) un cliente HTTP sin cabecera `Authorization`
- **WHEN** (Cuando) solicita `GET /api/dashboard/summary`
- **THEN** (Entonces) el sistema responde `401` con `Content-Type: application/problem+json`
- **AND** el cuerpo tiene `code` = `unauthenticated`, `status` = 401, `type` = `https://asuntos-horas.example/problems/unauthenticated`, `detail` en español y `traceId`
- **AND** el cuerpo no contiene `byMatter`, `byLawyer` ni `totals`

#### Scenario: Token expirado

- **GIVEN** (Dado) un usuario cuyo token de acceso expiró
- **WHEN** (Cuando) solicita `GET /api/dashboard/summary` con ese token
- **THEN** (Entonces) el sistema responde `401` con `code` = `unauthenticated`

### Requirement: Ventana por defecto de 30 días en la zona de la firma

Cuando la petición no incluye `from` ni `to`, el sistema SHALL (DEBE) agregar los registros de horas cuya fecha de trabajo (`workDate`) esté en el intervalo inclusivo `[hoy-29, hoy]`, donde "hoy" es la fecha actual en la zona horaria `America/La_Paz`. La respuesta SHALL (DEBE) incluir la ventana efectiva en los campos `from` y `to` (fechas ISO `YYYY-MM-DD`). El sistema SHALL NOT (NO DEBE) incluir registros con fecha de trabajo fuera de esa ventana.

#### Scenario: Límites de la ventana por defecto

- **GIVEN** (Dado) que hoy en `America/La_Paz` es 2026-11-07
- **AND** existen registros `APPROVED` de 2 h con fecha 2026-10-09 (hoy-29), de 3 h con fecha 2026-11-07 (hoy) y de 5 h con fecha 2026-10-08 (hoy-30)
- **WHEN** (Cuando) un socio solicita `GET /api/dashboard/summary` sin parámetros
- **THEN** (Entonces) el sistema responde `200` con `from` = `2026-10-09` y `to` = `2026-11-07`
- **AND** `totals.approvedHours` = 5.0 (incluye hoy-29 y hoy, excluye hoy-30)

#### Scenario: "Hoy" se calcula en la zona de la firma y no en UTC

- **GIVEN** (Dado) que el instante actual es 2026-11-08T02:00Z, que en `America/La_Paz` corresponde a 2026-11-07 22:00
- **WHEN** (Cuando) un socio solicita `GET /api/dashboard/summary` sin parámetros
- **THEN** (Entonces) la respuesta tiene `to` = `2026-11-07` y `from` = `2026-10-09`

### Requirement: Ventana personalizada con from y to

El sistema SHALL (DEBE) aceptar los parámetros de consulta opcionales `from` y `to` (fechas ISO `YYYY-MM-DD`, ambos inclusivos) y, cuando ambos vienen y son válidos, agregar solo los registros con fecha de trabajo en `[from, to]`, devolviendo esa misma ventana en la respuesta. El rango SHALL (DEBE) admitir como máximo 92 días contando ambos extremos.

#### Scenario: Rango válido de 92 días

- **GIVEN** (Dado) un socio autenticado
- **WHEN** (Cuando) solicita `GET /api/dashboard/summary?from=2026-08-08&to=2026-11-07` (92 días inclusive)
- **THEN** (Entonces) el sistema responde `200` con `from` = `2026-08-08` y `to` = `2026-11-07`
- **AND** los totales solo consideran registros con fecha de trabajo entre esas dos fechas, inclusive

#### Scenario: Rango de un solo día

- **GIVEN** (Dado) un socio autenticado y un registro `PENDING` de 1,5 h con fecha 2026-11-01
- **WHEN** (Cuando) solicita `GET /api/dashboard/summary?from=2026-11-01&to=2026-11-01`
- **THEN** (Entonces) el sistema responde `200` con `totals.pendingHours` = 1.5

### Requirement: Rango máximo de 92 días

El sistema SHALL (DEBE) responder `400` con `application/problem+json`, `code` = `validation-error` y un elemento en `errors[]` con `field` = `to` y `message` en español cuando el rango entre `from` y `to`, contando ambos extremos, supera 92 días. El sistema SHALL NOT (NO DEBE) devolver datos agregados en ese caso.

#### Scenario: Rango mayor a 92 días

- **GIVEN** (Dado) un socio autenticado
- **WHEN** (Cuando) solicita `GET /api/dashboard/summary?from=2026-08-07&to=2026-11-07` (93 días inclusive)
- **THEN** (Entonces) el sistema responde `400` con `code` = `validation-error`
- **AND** `errors[]` contiene un elemento con `field` = `to` y un mensaje en español que indica el máximo de 92 días
- **AND** el cuerpo no contiene `byMatter`, `byLawyer` ni `totals`

### Requirement: Orden de las fechas de la ventana

El sistema SHALL (DEBE) responder `400` con `application/problem+json`, `code` = `validation-error` y un elemento en `errors[]` con `field` = `from` y `message` en español cuando `from` es posterior a `to`. El sistema SHALL NOT (NO DEBE) devolver datos agregados en ese caso.

#### Scenario: from posterior a to

- **GIVEN** (Dado) un abogado autenticado
- **WHEN** (Cuando) solicita `GET /api/dashboard/summary?from=2026-11-07&to=2026-11-01`
- **THEN** (Entonces) el sistema responde `400` con `code` = `validation-error`
- **AND** `errors[]` contiene un elemento con `field` = `from`

### Requirement: Parámetros de la ventana enviados juntos

El sistema SHALL (DEBE) responder `400` con `application/problem+json`, `code` = `validation-error` y un elemento en `errors[]` cuyo `field` es el parámetro ausente cuando la petición incluye solo uno de `from` o `to`. El sistema SHALL NOT (NO DEBE) completar el parámetro ausente con un valor por defecto ni devolver datos agregados en ese caso.

#### Scenario: Solo se envía from

- **GIVEN** (Dado) un socio autenticado
- **WHEN** (Cuando) solicita `GET /api/dashboard/summary?from=2026-11-01`
- **THEN** (Entonces) el sistema responde `400` con `code` = `validation-error`
- **AND** `errors[]` contiene un elemento con `field` = `to`

#### Scenario: Solo se envía to

- **GIVEN** (Dado) un socio autenticado
- **WHEN** (Cuando) solicita `GET /api/dashboard/summary?to=2026-11-07`
- **THEN** (Entonces) el sistema responde `400` con `code` = `validation-error`
- **AND** `errors[]` contiene un elemento con `field` = `from`

### Requirement: Formato ISO de las fechas de la ventana

El sistema SHALL (DEBE) responder `400` con `application/problem+json`, `code` = `validation-error` y un elemento en `errors[]` por cada parámetro (`from` o `to`) que no sea una fecha ISO `YYYY-MM-DD` válida, con `message` en español. El sistema SHALL NOT (NO DEBE) devolver datos agregados en ese caso.

#### Scenario: Fecha con formato inválido

- **GIVEN** (Dado) un socio autenticado
- **WHEN** (Cuando) solicita `GET /api/dashboard/summary?from=07-11-2026&to=2026-11-07`
- **THEN** (Entonces) el sistema responde `400` con `code` = `validation-error`
- **AND** `errors[]` contiene un elemento con `field` = `from`

#### Scenario: Fecha inexistente

- **GIVEN** (Dado) un socio autenticado
- **WHEN** (Cuando) solicita `GET /api/dashboard/summary?from=2026-11-01&to=2026-02-30`
- **THEN** (Entonces) el sistema responde `400` con `code` = `validation-error`
- **AND** `errors[]` contiene un elemento con `field` = `to`

### Requirement: Horas agregadas por asunto

La respuesta SHALL (DEBE) incluir `byMatter`, una lista con una fila por asunto que tenga al menos un registro contado en la ventana, con `matterId`, `matterCode`, `matterTitle`, `approvedHours` (suma de registros `APPROVED`) y `pendingHours` (suma de registros `PENDING`). Las horas SHALL (DEBE) expresarse como número decimal con una décima (por ejemplo, 1.5). La lista SHALL (DEBE) ordenarse por `matterCode` ascendente.

#### Scenario: Suma por asunto separando aprobadas y pendientes

- **GIVEN** (Dado) que en la ventana el asunto `MAT-001` tiene registros `APPROVED` de 2 h y 1,5 h y un registro `PENDING` de 0,5 h, y el asunto `MAT-002` tiene un registro `PENDING` de 4 h
- **WHEN** (Cuando) un socio solicita el resumen
- **THEN** (Entonces) `byMatter` tiene dos filas en orden `MAT-001`, `MAT-002`
- **AND** la fila de `MAT-001` tiene `approvedHours` = 3.5 y `pendingHours` = 0.5, con su `matterId` y `matterTitle`
- **AND** la fila de `MAT-002` tiene `approvedHours` = 0.0 y `pendingHours` = 4.0

### Requirement: Horas agregadas por abogado

La respuesta SHALL (DEBE) incluir `byLawyer`, una lista con una fila por abogado que tenga al menos un registro contado en la ventana (salvo la fila propia del `LAWYER`, que aparece siempre; ver "Alcance del abogado"), con `lawyerId`, `lawyerName`, `approvedHours` y `pendingHours`, con el mismo formato decimal que `byMatter`. La lista SHALL (DEBE) ordenarse por `lawyerName` ascendente.

#### Scenario: Suma por abogado

- **GIVEN** (Dado) que en la ventana la abogada "Ana Pérez" (sintética) tiene 6 h `APPROVED` y 2 h `PENDING` repartidas en dos asuntos, y el abogado "Bruno Rojas" (sintético) tiene 1 h `PENDING`
- **WHEN** (Cuando) un socio solicita el resumen
- **THEN** (Entonces) `byLawyer` tiene dos filas en orden "Ana Pérez", "Bruno Rojas"
- **AND** la fila de "Ana Pérez" tiene `approvedHours` = 6.0 y `pendingHours` = 2.0
- **AND** la fila de "Bruno Rojas" tiene `approvedHours` = 0.0 y `pendingHours` = 1.0

### Requirement: Totales del período

La respuesta SHALL (DEBE) incluir `totals` con `approvedHours` y `pendingHours`, iguales a la suma de las horas aprobadas y pendientes contadas en la ventana dentro del alcance del usuario. La suma de cada columna de `byMatter` y la suma de cada columna de `byLawyer` SHALL (DEBE) coincidir con `totals`.

#### Scenario: Totales consistentes con las filas

- **GIVEN** (Dado) los registros del escenario "Suma por asunto separando aprobadas y pendientes"
- **WHEN** (Cuando) un socio solicita el resumen
- **THEN** (Entonces) `totals.approvedHours` = 3.5 y `totals.pendingHours` = 4.5
- **AND** la suma de `approvedHours` y de `pendingHours` en `byLawyer` es igual a esos totales

### Requirement: Exclusión de registros rechazados

El sistema SHALL NOT (NO DEBE) contar registros en estado `REJECTED` en `byMatter`, `byLawyer` ni `totals`. Un asunto o abogado cuyos únicos registros en la ventana estén rechazados SHALL NOT (NO DEBE) aparecer como fila, salvo la fila propia de un `LAWYER`, que aparece con horas en cero (ver "Alcance del abogado").

#### Scenario: Registro rechazado no suma

- **GIVEN** (Dado) que en la ventana el asunto `MAT-003` solo tiene un registro `REJECTED` de 8 h y el asunto `MAT-001` tiene un registro `APPROVED` de 1 h
- **WHEN** (Cuando) un socio solicita el resumen
- **THEN** (Entonces) `byMatter` contiene solo la fila de `MAT-001`
- **AND** `totals.approvedHours` = 1.0 y `totals.pendingHours` = 0.0

### Requirement: Alcance del socio

Para un usuario con rol `PARTNER`, el sistema SHALL (DEBE) agregar los registros de todos los abogados y todos los asuntos.

#### Scenario: El socio ve a todos los abogados

- **GIVEN** (Dado) que en la ventana hay registros contados de tres abogados distintos en cuatro asuntos
- **WHEN** (Cuando) un usuario `PARTNER` solicita el resumen
- **THEN** (Entonces) `byLawyer` tiene tres filas y `byMatter` tiene cuatro filas

### Requirement: Alcance del abogado

Para un usuario con rol `LAWYER`, el sistema SHALL (DEBE) agregar únicamente los registros de los que es autor. `byLawyer` SHALL (DEBE) contener siempre exactamente una fila, la del propio abogado, también sin horas contadas en la ventana, con `approvedHours` = 0.0 y `pendingHours` = 0.0. `byMatter` y `totals` SHALL (DEBE) reflejar solo sus horas. El sistema SHALL NOT (NO DEBE) exponer filas, nombres ni horas de otros abogados, ni asuntos en los que solo otros abogados registraron horas.

#### Scenario: El abogado solo ve sus horas

- **GIVEN** (Dado) que en la ventana la abogada "Ana Pérez" tiene 3 h `APPROVED` en `MAT-001`, y el abogado "Bruno Rojas" tiene 5 h `APPROVED` en `MAT-001` y 2 h `PENDING` en `MAT-002`
- **WHEN** (Cuando) "Ana Pérez" (rol `LAWYER`) solicita el resumen
- **THEN** (Entonces) el sistema responde `200`
- **AND** `byLawyer` tiene exactamente una fila, la de "Ana Pérez", con `approvedHours` = 3.0
- **AND** `byMatter` tiene solo la fila de `MAT-001` con `approvedHours` = 3.0 y `pendingHours` = 0.0
- **AND** `totals.approvedHours` = 3.0 y `totals.pendingHours` = 0.0
- **AND** la respuesta no contiene "Bruno Rojas" ni `MAT-002`

#### Scenario: El abogado no puede ampliar su alcance con parámetros

- **GIVEN** (Dado) la abogada "Ana Pérez" autenticada con rol `LAWYER`
- **WHEN** (Cuando) solicita `GET /api/dashboard/summary?from=2026-10-01&to=2026-11-07&lawyerId=<id de Bruno Rojas>`
- **THEN** (Entonces) la respuesta solo contiene horas de "Ana Pérez" (el parámetro desconocido se ignora)

### Requirement: Período sin horas

Cuando no hay registros contados en la ventana dentro del alcance del usuario, el sistema SHALL (DEBE) responder `200` con `byMatter` como lista vacía y `totals.approvedHours` = 0.0 y `totals.pendingHours` = 0.0. Para un `PARTNER`, `byLawyer` SHALL (DEBE) ser una lista vacía; para un `LAWYER`, `byLawyer` SHALL (DEBE) contener solo su propia fila con horas en cero (requisito "Alcance del abogado"). El sistema SHALL NOT (NO DEBE) responder `404` por un período vacío.

#### Scenario: Ventana sin registros para el socio

- **GIVEN** (Dado) que no existen registros con fecha de trabajo en la ventana
- **WHEN** (Cuando) un socio solicita el resumen
- **THEN** (Entonces) el sistema responde `200` con `byMatter` = `[]`, `byLawyer` = `[]` y `totals` = `{approvedHours: 0.0, pendingHours: 0.0}`

#### Scenario: Abogado sin horas propias en la ventana

- **GIVEN** (Dado) que otros abogados tienen registros en la ventana pero la abogada "Ana Pérez" no tiene ninguno contado
- **WHEN** (Cuando) "Ana Pérez" (rol `LAWYER`) solicita el resumen
- **THEN** (Entonces) el sistema responde `200` con `byMatter` = `[]` y totales en cero
- **AND** `byLawyer` tiene exactamente una fila, la de "Ana Pérez", con `approvedHours` = 0.0 y `pendingHours` = 0.0
- **AND** la respuesta no contiene nombres ni horas de otros abogados

### Requirement: Pantalla Tablero

El frontend SHALL (DEBE) ofrecer, a usuarios autenticados con rol `PARTNER` o `LAWYER`, una pantalla titulada "Tablero" accesible desde la navegación principal. La pantalla SHALL (DEBE) mostrar siempre la ventana fija de los últimos 30 días, con el período visible como texto, y SHALL NOT (NO DEBE) ofrecer controles para cambiar el rango. SHALL (DEBE) mostrar los totales del período como texto: "Aprobadas: X h" y "Pendientes: Y h", con coma decimal.

#### Scenario: El socio abre el Tablero

- **GIVEN** (Dado) un socio autenticado y registros contados en los últimos 30 días
- **WHEN** (Cuando) entra a "Tablero" desde la navegación
- **THEN** (Entonces) ve el encabezado "Tablero", el texto del período de 30 días (por ejemplo, "Últimos 30 días: 09/10/2026 – 07/11/2026") y los totales "Aprobadas" y "Pendientes"
- **AND** no hay campos para elegir fechas

#### Scenario: El abogado ve solo su fila

- **GIVEN** (Dado) una abogada autenticada con horas propias en los últimos 30 días
- **WHEN** (Cuando) entra a "Tablero"
- **THEN** (Entonces) la tabla por abogado muestra una sola fila con su nombre
- **AND** no aparecen nombres de otros abogados en la pantalla

#### Scenario: Sesión expirada en el Tablero

- **GIVEN** (Dado) un usuario cuyo token expiró
- **WHEN** (Cuando) abre "Tablero"
- **THEN** (Entonces) la aplicación lo lleva a la pantalla de inicio de sesión sin mostrar datos del tablero

#### Scenario: Error del servidor al cargar

- **GIVEN** (Dado) que `GET /api/dashboard/summary` responde con un error distinto de `401`
- **WHEN** (Cuando) el usuario abre "Tablero"
- **THEN** (Entonces) la pantalla muestra un mensaje de error en español anunciado a tecnologías de asistencia (`role="alert"`) y no muestra tablas con datos parciales

### Requirement: Tablas accesibles por asunto y por abogado

La pantalla "Tablero" SHALL (DEBE) presentar dos tablas HTML, "Horas por asunto" y "Horas por abogado", cada una con un `<caption>` descriptivo, encabezados de columna `<th scope="col">` y la primera celda de cada fila como `<th scope="row">` (código y título del asunto, o nombre del abogado). Cada fila SHALL (DEBE) mostrar las horas aprobadas y pendientes como texto numérico.

#### Scenario: Estructura accesible de la tabla por asunto

- **GIVEN** (Dado) un socio en "Tablero" con datos en el período
- **WHEN** (Cuando) se inspecciona la tabla por asunto
- **THEN** (Entonces) tiene `<caption>` con el texto "Horas por asunto"
- **AND** sus encabezados de columna usan `scope="col"` y la celda del asunto en cada fila usa `<th scope="row">`
- **AND** un lector de pantalla puede asociar cada valor de horas con su asunto y su columna

#### Scenario: Estructura accesible de la tabla por abogado

- **GIVEN** (Dado) un socio en "Tablero" con datos en el período
- **WHEN** (Cuando) se inspecciona la tabla por abogado
- **THEN** (Entonces) tiene `<caption>` con el texto "Horas por abogado", encabezados `scope="col"` y la celda del nombre como `<th scope="row">`

### Requirement: Barras de horas distinguibles sin depender del color

Cada fila de ambas tablas SHALL (DEBE) incluir una barra horizontal hecha con CSS cuya longitud de cada segmento sea proporcional a las horas aprobadas y pendientes de la fila. Los segmentos SHALL (DEBE) distinguirse por texto visible ("Aprobadas: X h", "Pendientes: Y h") además del color, y SHALL NOT (NO DEBE) depender solo del color para transmitir la diferencia. Una fila con cero horas en una categoría SHALL (DEBE) mostrar ese valor como "0 h".

#### Scenario: Diferencia entre aprobadas y pendientes visible en escala de grises

- **GIVEN** (Dado) una fila con 3,5 h aprobadas y 0,5 h pendientes
- **WHEN** (Cuando) se muestra el Tablero
- **THEN** (Entonces) la barra de la fila muestra un segmento de aprobadas siete veces más largo que el de pendientes
- **AND** junto a la barra se lee "Aprobadas: 3,5 h" y "Pendientes: 0,5 h"
- **AND** la información se entiende sin percibir colores

#### Scenario: Categoría en cero

- **GIVEN** (Dado) una fila con 4 h pendientes y ninguna aprobada
- **WHEN** (Cuando) se muestra el Tablero
- **THEN** (Entonces) la fila muestra "Aprobadas: 0 h" y "Pendientes: 4 h"

### Requirement: Estado vacío del Tablero

Cuando el resumen no tiene horas en el período (totales en cero), la pantalla "Tablero" SHALL (DEBE) mostrar el mensaje "No hay horas registradas en los últimos 30 días." y los totales en cero, y SHALL NOT (NO DEBE) mostrar tablas vacías sin explicación. Para un `LAWYER`, la fila propia en cero de `byLawyer` SHALL (DEBE) mostrarse junto al mensaje.

#### Scenario: Tablero sin datos

- **GIVEN** (Dado) que el resumen devuelve `byMatter` vacío y totales en cero
- **WHEN** (Cuando) el usuario abre "Tablero"
- **THEN** (Entonces) ve "No hay horas registradas en los últimos 30 días."
- **AND** ve "Aprobadas: 0 h" y "Pendientes: 0 h"

#### Scenario: Abogado sin horas en el Tablero

- **GIVEN** (Dado) un abogado autenticado sin horas contadas en los últimos 30 días
- **WHEN** (Cuando) abre "Tablero"
- **THEN** (Entonces) ve "No hay horas registradas en los últimos 30 días."
- **AND** la tabla "Horas por abogado" muestra una sola fila con su nombre y "Aprobadas: 0 h", "Pendientes: 0 h"

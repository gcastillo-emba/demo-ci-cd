## MODIFIED Requirements

### Requirement: Campos controlados por el servidor

En `POST /api/time-entries` y en `PUT /api/time-entries/{id}`, los campos `id`, `lawyerId`, `lawyerName`, `matterCode`, `status`, `rejectionReason`, `createdAt` y `editableUntil` enviados en el cuerpo SHALL (DEBEN) ignorarse en silencio: el registro SHALL (DEBE) pertenecer siempre al abogado autenticado, nacer y seguir en estado `PENDING` tras una edición, sin `rejectionReason`, y con `createdAt` fijado por el servidor con su `Clock`.

#### Scenario: Alta con campos de solo lectura

- **GIVEN** (Dado) un abogado A autenticado, otro abogado B y un asunto `OPEN`
- **AND** hoy es 2026-11-07 en `America/La_Paz`
- **WHEN** (Cuando) A envía `POST /api/time-entries` con datos válidos más `lawyerId` = B, `status` = `APPROVED`, `rejectionReason` = "x" y `createdAt` = 2026-01-01T00:00:00Z
- **THEN** (Entonces) la respuesta es 201
- **AND** el registro devuelto tiene `lawyerId` = A, `status` = `PENDING`, sin `rejectionReason` y `createdAt` del 2026-11-07 en `America/La_Paz`
- **AND** B no tiene ningún registro nuevo

#### Scenario: Edición con campos de solo lectura

- **GIVEN** (Dado) un registro `PENDING` editable del abogado A con `version` vigente
- **WHEN** (Cuando) A envía `PUT /api/time-entries/{id}` con datos válidos, la `version` vigente y además `lawyerId` de otro abogado, `status` = `APPROVED` y `createdAt` = 2026-01-01T00:00:00Z
- **THEN** (Entonces) la respuesta es 200
- **AND** el registro sigue con `lawyerId` = A, `status` = `PENDING` y su `createdAt` y `editableUntil` originales

### Requirement: Rechazo de registros con motivo obligatorio

Un usuario `PARTNER` SHALL (DEBE) poder rechazar un registro `PENDING` mediante `POST /api/time-entries/{id}/reject` con `reason` de 1 a 500 caracteres (sin espacios al inicio y al final), respondiendo 200 con el registro en estado `REJECTED` y su `rejectionReason`. El sistema SHALL (DEBE) responder 422 `rejection-reason-required` si `reason` falta, está en blanco o supera 500 caracteres; 409 `entry-not-pending` si el registro no está `PENDING`; y 403 `forbidden` si quien invoca es `LAWYER`.

#### Scenario: Socio rechaza con motivo

- **GIVEN** (Dado) un socio autenticado y un registro `PENDING`
- **WHEN** (Cuando) envía `POST /api/time-entries/{id}/reject` con `reason` = "Descripción insuficiente"
- **THEN** (Entonces) la respuesta es 200
- **AND** el registro tiene `status` = `REJECTED` y `rejectionReason` = "Descripción insuficiente"

#### Scenario: Rechazo sin motivo

- **GIVEN** (Dado) un socio autenticado y un registro `PENDING`
- **WHEN** (Cuando) envía `POST /api/time-entries/{id}/reject` con `reason` vacío o ausente
- **THEN** (Entonces) la respuesta es 422 con `code` = `rejection-reason-required`
- **AND** el registro sigue `PENDING`

#### Scenario: Motivo de 501 caracteres

- **GIVEN** (Dado) un socio autenticado y un registro `PENDING`
- **WHEN** (Cuando) rechaza con un `reason` de 501 caracteres
- **THEN** (Entonces) la respuesta es 422 con `code` = `rejection-reason-required`

#### Scenario: Rechazar un registro ya rechazado

- **GIVEN** (Dado) un socio autenticado y un registro `REJECTED`
- **WHEN** (Cuando) envía `POST /api/time-entries/{id}/reject` con un motivo válido
- **THEN** (Entonces) la respuesta es 409 con `code` = `entry-not-pending`

#### Scenario: Abogado intenta rechazar

- **GIVEN** (Dado) un abogado autenticado y un registro `PENDING`
- **WHEN** (Cuando) envía `POST /api/time-entries/{id}/reject` con un motivo válido
- **THEN** (Entonces) la respuesta es 403 con `code` = `forbidden`

### Requirement: Pantalla "Aprobaciones"

El sistema SHALL (DEBE) ofrecer al socio la cola "Aprobaciones" con los registros `PENDING` de todos los abogados (abogado, asunto, fecha, horas, descripción), con las acciones "Aprobar" y "Rechazar". "Rechazar" SHALL (DEBE) abrir un diálogo que exige un motivo de 1 a 500 caracteres; el botón de confirmación SHALL NOT (NO DEBE) habilitarse con el motivo vacío. Tras aprobarlo o rechazarlo, SHALL (DEBE) desaparecer de la cola. La opción "Aprobaciones" SHALL NOT (NO DEBE) mostrarse a un abogado.

#### Scenario: Aprobar desde la cola

- **GIVEN** (Dado) un socio en "Aprobaciones" con un registro pendiente
- **WHEN** (Cuando) pulsa "Aprobar" en ese registro
- **THEN** (Entonces) el registro desaparece de la cola y se muestra una confirmación en español

#### Scenario: Rechazar exige motivo

- **GIVEN** (Dado) un socio que abrió el diálogo "Rechazar" de un registro pendiente
- **WHEN** (Cuando) el campo motivo está vacío
- **THEN** (Entonces) el botón "Confirmar rechazo" está deshabilitado
- **AND** al escribir un motivo y confirmar, el registro desaparece de la cola

#### Scenario: Registro ya resuelto por otro socio

- **GIVEN** (Dado) un socio en "Aprobaciones" con un registro que otro socio ya aprobó
- **WHEN** (Cuando) pulsa "Aprobar" y recibe 409 `entry-not-pending`
- **THEN** (Entonces) se muestra en español que el registro ya no está pendiente y la cola se recarga

#### Scenario: Abogado no ve la opción

- **GIVEN** (Dado) un abogado autenticado
- **WHEN** (Cuando) abre la aplicación
- **THEN** (Entonces) el menú no muestra "Aprobaciones"

## REMOVED Requirements

### Requirement: Listado de registros con filtros y paginación

**Reason**: Texto de más de 500 caracteres; se parte en cinco requisitos: filtros y paginación, alcance por rol, orden, fecha límite de edición y validación de parámetros.

**Migration**: Sin cambio de comportamiento; los escenarios pasan sin cambios a los requisitos nuevos de la sección ADDED.

## ADDED Requirements

### Requirement: Filtros y paginación del listado de registros

El sistema SHALL (DEBE) exponer `GET /api/time-entries` con filtros opcionales `matterId`, `lawyerId`, `status`, `from` y `to` (rango inclusivo sobre `workDate`) y paginación `page` (base 0, por defecto 0) y `size` (por defecto 20, máximo 100), respondiendo 200 con `{items, page, size, totalItems, totalPages}`.

#### Scenario: Socio filtra pendientes de un abogado

- **GIVEN** (Dado) un socio autenticado y registros de varios abogados en distintos estados
- **WHEN** (Cuando) invoca `GET /api/time-entries?lawyerId={A}&status=PENDING`
- **THEN** (Entonces) la respuesta es 200 y todos los `items` son `PENDING` y del abogado A

#### Scenario: Socio filtra por asunto y rango de fechas

- **GIVEN** (Dado) un socio autenticado y registros del asunto M entre 2026-10-01 y 2026-11-07
- **WHEN** (Cuando) invoca `GET /api/time-entries?matterId={M}&from=2026-11-01&to=2026-11-05`
- **THEN** (Entonces) todos los `items` son del asunto M con `workDate` entre 2026-11-01 y 2026-11-05, ambos incluidos

#### Scenario: Paginación por defecto

- **GIVEN** (Dado) un socio autenticado y 45 registros
- **WHEN** (Cuando) invoca `GET /api/time-entries` sin parámetros de paginación
- **THEN** (Entonces) la respuesta tiene `page` = 0, `size` = 20, 20 `items`, `totalItems` = 45 y `totalPages` = 3

### Requirement: Alcance del listado según el rol

En `GET /api/time-entries`, para un `PARTNER` el sistema SHALL (DEBE) devolver registros de todos los abogados que cumplan los filtros. Para un `LAWYER` SHALL (DEBE) devolver solo sus propios registros y SHALL (DEBE) ignorar `lawyerId`.

#### Scenario: Abogado solo ve lo suyo

- **GIVEN** (Dado) un abogado A autenticado y registros de los abogados A y B
- **WHEN** (Cuando) invoca `GET /api/time-entries?lawyerId={B}`
- **THEN** (Entonces) la respuesta es 200 y todos los `items` pertenecen al abogado A

### Requirement: Orden del listado de registros

En `GET /api/time-entries`, el parámetro opcional `sort` SHALL (DEBE) aceptar solo `workDate,desc` (valor por defecto: `workDate` descendente y, a igual fecha, `createdAt` descendente) y `createdAt,asc` (`createdAt` ascendente).

#### Scenario: Orden por defecto

- **GIVEN** (Dado) un abogado con registros de `workDate` 2026-11-03, 2026-11-05 y dos de 2026-11-04 creados en distinto momento
- **WHEN** (Cuando) invoca `GET /api/time-entries` sin `sort`
- **THEN** (Entonces) los `items` vienen en orden 2026-11-05, 2026-11-04 (el creado más tarde), 2026-11-04 (el creado antes), 2026-11-03

#### Scenario: Orden de la cola de aprobaciones

- **GIVEN** (Dado) un socio autenticado y tres registros `PENDING` creados en momentos distintos
- **WHEN** (Cuando) invoca `GET /api/time-entries?status=PENDING&sort=createdAt,asc`
- **THEN** (Entonces) los `items` vienen del más antiguo al más reciente por `createdAt`

### Requirement: Fecha límite de edición en el listado

Cada elemento de `items` de `GET /api/time-entries` SHALL (DEBE) incluir `editableUntil`: la última fecha (en `America/La_Paz`) en que el registro puede editarse, igual a su fecha de creación en La Paz más 7 días.

#### Scenario: Fecha límite de edición en la respuesta

- **GIVEN** (Dado) un registro creado el 2026-10-31 (fecha en `America/La_Paz`)
- **WHEN** (Cuando) su autor invoca `GET /api/time-entries`
- **THEN** (Entonces) el elemento de ese registro tiene `editableUntil` = 2026-11-07

### Requirement: Validación de parámetros del listado

En `GET /api/time-entries`, el sistema SHALL (DEBE) responder 400 `validation-error` si `size` es mayor que 100 o menor que 1, si `page` es negativo, si `status` o `sort` no son valores válidos o si `from` es posterior a `to`.

#### Scenario: Tamaño de página excesivo

- **GIVEN** (Dado) un usuario autenticado
- **WHEN** (Cuando) invoca `GET /api/time-entries?size=101`
- **THEN** (Entonces) la respuesta es 400 con `code` = `validation-error` y `errors[].field` = `size`

#### Scenario: Rango de fechas invertido

- **GIVEN** (Dado) un usuario autenticado
- **WHEN** (Cuando) invoca `GET /api/time-entries?from=2026-11-05&to=2026-11-01`
- **THEN** (Entonces) la respuesta es 400 con `code` = `validation-error`

#### Scenario: Orden no admitido

- **GIVEN** (Dado) un usuario autenticado
- **WHEN** (Cuando) invoca `GET /api/time-entries?sort=hours,desc`
- **THEN** (Entonces) la respuesta es 400 con `code` = `validation-error` y `errors[].field` = `sort`

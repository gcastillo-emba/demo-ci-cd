## Purpose

Permite que los abogados registren las horas trabajadas en asuntos abiertos y que los socios las aprueben o rechacen, aplicando las reglas de negocio de la firma (décimas de hora, límite diario de 24 h, fecha no futura en `America/La_Paz`, ventana de edición de 7 días y estados terminales).

## ADDED Requirements

### Requirement: Autenticación obligatoria en registros de horas

El sistema SHALL (DEBE) exigir un token Bearer válido en todos los endpoints bajo `/api/time-entries` y SHALL (DEBE) responder 401 con Problem Details `code` = `unauthenticated` cuando falte o sea inválido.

#### Scenario: Petición sin token

- **GIVEN** (Dado) una petición sin cabecera `Authorization`
- **WHEN** (Cuando) se invoca `GET /api/time-entries`
- **THEN** (Entonces) la respuesta es 401 `application/problem+json`
- **AND** el campo `code` es `unauthenticated`

#### Scenario: Token expirado al registrar horas

- **GIVEN** (Dado) un token JWT expirado
- **WHEN** (Cuando) se invoca `POST /api/time-entries`
- **THEN** (Entonces) la respuesta es 401 con `code` = `unauthenticated`
- **AND** no se crea ningún registro

### Requirement: Registro de horas por un abogado

El sistema SHALL (DEBE) permitir que un usuario con rol `LAWYER` registre horas en un asunto `OPEN` mediante `POST /api/time-entries` con `matterId`, `workDate`, `hours` y `description`, responder 201 con el registro creado en estado `PENDING`, asignado al abogado autenticado e incluyendo su `version`. El sistema SHALL (DEBE) responder 404 `not-found` cuando el asunto no exista.

#### Scenario: Registro válido

- **GIVEN** (Dado) un abogado autenticado y un asunto `OPEN`
- **AND** hoy es 2026-11-07 en `America/La_Paz`
- **WHEN** (Cuando) envía `POST /api/time-entries` con `workDate` = 2026-11-06, `hours` = 1.5 y una descripción de 40 caracteres
- **THEN** (Entonces) la respuesta es 201
- **AND** el registro devuelto tiene `status` = `PENDING`, `hours` = 1.5, `lawyerId` igual al abogado autenticado y un `version`

#### Scenario: Asunto inexistente

- **GIVEN** (Dado) un abogado autenticado
- **WHEN** (Cuando) registra horas con un `matterId` que no existe
- **THEN** (Entonces) la respuesta es 404 con `code` = `not-found`

### Requirement: Validación de formato del registro

El sistema SHALL (DEBE) responder 400 con `code` = `validation-error` y un elemento en `errors[]` por campo inválido cuando falte `matterId`, `workDate`, `hours` o `description`, cuando un valor no tenga el tipo esperado, o cuando `description` (sin espacios al inicio y al final) tenga menos de 1 o más de 2000 caracteres. Esta validación SHALL (DEBE) aplicarse igual en el alta y en la edición.

#### Scenario: Descripción vacía

- **GIVEN** (Dado) un abogado autenticado
- **WHEN** (Cuando) registra horas con `description` = " "
- **THEN** (Entonces) la respuesta es 400 con `code` = `validation-error`
- **AND** `errors[]` contiene un elemento con `field` = `description` y un `message` en español

#### Scenario: Descripción de 2001 caracteres

- **GIVEN** (Dado) un abogado autenticado
- **WHEN** (Cuando) registra horas con una descripción de 2001 caracteres
- **THEN** (Entonces) la respuesta es 400 con `code` = `validation-error` y `errors[].field` = `description`

#### Scenario: Descripción de 2000 caracteres

- **GIVEN** (Dado) un abogado autenticado y un asunto `OPEN`
- **WHEN** (Cuando) registra horas válidas con una descripción de exactamente 2000 caracteres
- **THEN** (Entonces) la respuesta es 201

#### Scenario: Campos obligatorios ausentes

- **GIVEN** (Dado) un abogado autenticado
- **WHEN** (Cuando) envía `POST /api/time-entries` sin `workDate` ni `hours`
- **THEN** (Entonces) la respuesta es 400 con `code` = `validation-error`
- **AND** `errors[]` contiene un elemento para `workDate` y otro para `hours`

### Requirement: Horas en décimas dentro de rango

El sistema SHALL (DEBE) aceptar solo horas múltiplos de 0,1 y SHALL (DEBE) responder 422 `hours-invalid-increment` cuando `hours` tenga más de un decimal significativo. El sistema SHALL (DEBE) aceptar solo horas mayores que 0 y menores o iguales a 24, y SHALL (DEBE) responder 422 `hours-out-of-range` en caso contrario.

#### Scenario: Límites válidos

- **GIVEN** (Dado) un abogado autenticado sin horas registradas el 2026-11-05 y un asunto `OPEN`
- **WHEN** (Cuando) registra `hours` = 0.1 para el 2026-11-05 y, en otra petición, `hours` = 23.9 para el mismo día
- **THEN** (Entonces) ambas respuestas son 201

#### Scenario: Incremento inválido

- **GIVEN** (Dado) un abogado autenticado y un asunto `OPEN`
- **WHEN** (Cuando) registra `hours` = 1.25
- **THEN** (Entonces) la respuesta es 422 con `code` = `hours-invalid-increment`

#### Scenario: Cero horas

- **GIVEN** (Dado) un abogado autenticado y un asunto `OPEN`
- **WHEN** (Cuando) registra `hours` = 0
- **THEN** (Entonces) la respuesta es 422 con `code` = `hours-out-of-range`

#### Scenario: Más de 24 horas

- **GIVEN** (Dado) un abogado autenticado y un asunto `OPEN`
- **WHEN** (Cuando) registra `hours` = 24.1
- **THEN** (Entonces) la respuesta es 422 con `code` = `hours-out-of-range`

#### Scenario: Horas negativas

- **GIVEN** (Dado) un abogado autenticado y un asunto `OPEN`
- **WHEN** (Cuando) registra `hours` = -1
- **THEN** (Entonces) la respuesta es 422 con `code` = `hours-out-of-range`

### Requirement: Límite diario de 24 horas por abogado

El sistema SHALL (DEBE) rechazar un alta cuando la suma de las horas `PENDING` y `APPROVED` del abogado para ese `workDate`, incluido el nuevo registro, supere 24, respondiendo 422 `daily-hours-exceeded` con los miembros de extensión `limitHours` (24) y `alreadyLoggedHours` (horas ya contabilizadas sin el nuevo registro). Las horas `REJECTED` SHALL NOT (NO DEBEN) contar para el límite. Las horas de otros abogados SHALL NOT (NO DEBEN) contar.

#### Scenario: Completar exactamente 24 horas

- **GIVEN** (Dado) un abogado con 16 h `PENDING` y 4 h `APPROVED` el 2026-11-05
- **WHEN** (Cuando) registra 4 h para el 2026-11-05
- **THEN** (Entonces) la respuesta es 201

#### Scenario: Superar el límite diario

- **GIVEN** (Dado) un abogado con 20 h entre `PENDING` y `APPROVED` el 2026-11-05
- **WHEN** (Cuando) registra 4.1 h para el 2026-11-05
- **THEN** (Entonces) la respuesta es 422 con `code` = `daily-hours-exceeded`
- **AND** el cuerpo incluye `limitHours` = 24 y `alreadyLoggedHours` = 20
- **AND** el `detail` en español indica cuántas horas quedan disponibles
- **AND** el campo `type` es `https://asuntos-horas.example/problems/daily-hours-exceeded`, `status` es 422 y `traceId` está presente y no vacío

#### Scenario: Las horas rechazadas no cuentan

- **GIVEN** (Dado) un abogado con 20 h `APPROVED` y 8 h `REJECTED` el 2026-11-05
- **WHEN** (Cuando) registra 4 h para el 2026-11-05
- **THEN** (Entonces) la respuesta es 201

#### Scenario: Las horas de otro abogado no cuentan

- **GIVEN** (Dado) otro abogado con 24 h `APPROVED` el 2026-11-05 y el abogado autenticado sin horas ese día
- **WHEN** (Cuando) el abogado autenticado registra 8 h para el 2026-11-05
- **THEN** (Entonces) la respuesta es 201

### Requirement: Altas y ediciones concurrentes respetan el límite diario

El sistema SHALL NOT (NO DEBE) aceptar operaciones concurrentes de alta o de edición (en cualquier combinación) del mismo abogado que afecten al mismo `workDate` cuyo resultado conjunto deje la suma de horas `PENDING` y `APPROVED` de ese día por encima de 24; como máximo SHALL (DEBE) tener éxito el subconjunto que respeta el límite y el resto SHALL (DEBE) recibir 422 `daily-hours-exceeded`.

#### Scenario: Dos altas simultáneas de 13 horas

- **GIVEN** (Dado) un abogado sin horas el 2026-11-05
- **WHEN** (Cuando) envía en paralelo dos `POST /api/time-entries` de 13 h cada uno para el 2026-11-05
- **THEN** (Entonces) exactamente una respuesta es 201 y la otra es 422 con `code` = `daily-hours-exceeded`
- **AND** la suma de horas `PENDING` del abogado ese día es 13

#### Scenario: Alta y edición simultáneas el mismo día

- **GIVEN** (Dado) un abogado con un único registro `PENDING` editable de 10 h el 2026-11-05
- **WHEN** (Cuando) envía en paralelo un `POST /api/time-entries` de 10 h para el 2026-11-05 y un `PUT /api/time-entries/{id}` que cambia ese registro a 14 h con la `version` vigente
- **THEN** (Entonces) exactamente una de las dos respuestas es exitosa (201 o 200) y la otra es 422 con `code` = `daily-hours-exceeded`
- **AND** la suma de horas `PENDING` del abogado ese día es como máximo 24

### Requirement: Fecha de trabajo no futura

El sistema SHALL (DEBE) rechazar con 422 `future-date` un `workDate` posterior a la fecha de hoy calculada en la zona horaria de la firma `America/La_Paz`, independientemente de la zona del cliente o del servidor.

#### Scenario: Registrar horas de hoy

- **GIVEN** (Dado) hoy es 2026-11-07 en `America/La_Paz`
- **WHEN** (Cuando) un abogado registra horas con `workDate` = 2026-11-07
- **THEN** (Entonces) la respuesta es 201

#### Scenario: Registrar horas de mañana

- **GIVEN** (Dado) hoy es 2026-11-07 en `America/La_Paz`
- **WHEN** (Cuando) un abogado registra horas con `workDate` = 2026-11-08
- **THEN** (Entonces) la respuesta es 422 con `code` = `future-date`

#### Scenario: Borde de medianoche entre UTC y La Paz

- **GIVEN** (Dado) el instante actual es 2026-11-08T02:30:00Z (2026-11-07 22:30 en `America/La_Paz`)
- **WHEN** (Cuando) un abogado registra horas con `workDate` = 2026-11-08
- **THEN** (Entonces) la respuesta es 422 con `code` = `future-date`

### Requirement: No se registran horas en asuntos cerrados

El sistema SHALL NOT (NO DEBE) permitir registrar horas en un asunto `CLOSED` y SHALL (DEBE) responder 422 `matter-closed`.

#### Scenario: Asunto cerrado

- **GIVEN** (Dado) un asunto con estado `CLOSED`
- **WHEN** (Cuando) un abogado registra horas válidas en ese asunto
- **THEN** (Entonces) la respuesta es 422 con `code` = `matter-closed`
- **AND** no se crea ningún registro

### Requirement: El socio no registra horas

El sistema SHALL NOT (NO DEBE) permitir que un usuario con rol `PARTNER` cree o edite registros de horas y SHALL (DEBE) responder 403 `forbidden`.

#### Scenario: Socio intenta registrar horas

- **GIVEN** (Dado) un socio autenticado y un asunto `OPEN`
- **WHEN** (Cuando) envía `POST /api/time-entries` con datos válidos
- **THEN** (Entonces) la respuesta es 403 con `code` = `forbidden`

#### Scenario: Socio intenta editar un registro

- **GIVEN** (Dado) un socio autenticado y un registro `PENDING` de un abogado
- **WHEN** (Cuando) envía `PUT /api/time-entries/{id}`
- **THEN** (Entonces) la respuesta es 403 con `code` = `forbidden`

### Requirement: Edición de un registro propio

El sistema SHALL (DEBE) permitir que el abogado autor edite `matterId`, `workDate`, `hours` y `description` de su registro mediante `PUT /api/time-entries/{id}` enviando la `version` vigente, respondiendo 200 con el registro actualizado y una `version` nueva. El sistema SHALL (DEBE) responder 403 `not-entry-owner` cuando el abogado no sea el autor y 404 `not-found` cuando el registro no exista.

#### Scenario: Edición válida

- **GIVEN** (Dado) un registro `PENDING` del abogado creado hace 2 días con `version` = 0
- **WHEN** (Cuando) el autor envía `PUT /api/time-entries/{id}` con `hours` = 3.5, una nueva descripción y `version` = 0
- **THEN** (Entonces) la respuesta es 200
- **AND** el registro devuelto tiene `hours` = 3.5, `status` = `PENDING` y una `version` distinta de 0

#### Scenario: Abogado edita un registro ajeno

- **GIVEN** (Dado) un registro `PENDING` del abogado A
- **WHEN** (Cuando) el abogado B envía `PUT /api/time-entries/{id}` sobre ese registro
- **THEN** (Entonces) la respuesta es 403 con `code` = `not-entry-owner`

#### Scenario: Registro inexistente

- **GIVEN** (Dado) un abogado autenticado
- **WHEN** (Cuando) envía `PUT /api/time-entries/{id}` con un `id` que no existe
- **THEN** (Entonces) la respuesta es 404 con `code` = `not-found`

### Requirement: Solo se editan registros pendientes

El sistema SHALL NOT (NO DEBE) permitir editar un registro en estado `APPROVED` o `REJECTED` y SHALL (DEBE) responder 409 `entry-not-pending`.

#### Scenario: Editar un registro aprobado

- **GIVEN** (Dado) un registro `APPROVED` del abogado
- **WHEN** (Cuando) el autor envía `PUT /api/time-entries/{id}` con la `version` vigente
- **THEN** (Entonces) la respuesta es 409 con `code` = `entry-not-pending`

#### Scenario: Editar un registro rechazado

- **GIVEN** (Dado) un registro `REJECTED` del abogado
- **WHEN** (Cuando) el autor envía `PUT /api/time-entries/{id}` con la `version` vigente
- **THEN** (Entonces) la respuesta es 409 con `code` = `entry-not-pending`

### Requirement: Ventana de edición de 7 días

El sistema SHALL (DEBE) permitir editar un registro `PENDING` solo mientras la fecha de hoy en `America/La_Paz` no sea posterior a la fecha de creación (en `America/La_Paz`) más 7 días calendario, y SHALL (DEBE) responder 422 `edit-window-expired` fuera de esa ventana.

#### Scenario: Último día de la ventana

- **GIVEN** (Dado) un registro `PENDING` creado el 2026-10-31 (fecha en `America/La_Paz`)
- **AND** hoy es 2026-11-07 en `America/La_Paz`
- **WHEN** (Cuando) el autor lo edita con datos válidos y la `version` vigente
- **THEN** (Entonces) la respuesta es 200

#### Scenario: Ventana vencida

- **GIVEN** (Dado) un registro `PENDING` creado el 2026-10-30 (fecha en `America/La_Paz`)
- **AND** hoy es 2026-11-07 en `America/La_Paz`
- **WHEN** (Cuando) el autor lo edita con datos válidos y la `version` vigente
- **THEN** (Entonces) la respuesta es 422 con `code` = `edit-window-expired`

### Requirement: Reglas de negocio en la edición

El sistema SHALL (DEBE) aplicar en la edición las mismas reglas que en el alta: 422 `hours-invalid-increment`, 422 `hours-out-of-range`, 422 `future-date`, 422 `matter-closed` (evaluada sobre el asunto destino, también cuando la edición no cambia `matterId` y el asunto original se cerró después de la carga) y 422 `daily-hours-exceeded`. Para el límite diario, las horas previas del propio registro editado SHALL NOT (NO DEBEN) contar en la suma.

#### Scenario: Las horas previas del propio registro no cuentan

- **GIVEN** (Dado) un abogado con un registro `PENDING` de 8 h y otros registros por 16 h el 2026-11-05
- **WHEN** (Cuando) edita ese registro a 8 h con la `version` vigente
- **THEN** (Entonces) la respuesta es 200

#### Scenario: La edición supera el límite diario

- **GIVEN** (Dado) un abogado con un registro `PENDING` de 8 h y otros registros por 16 h el 2026-11-05
- **WHEN** (Cuando) edita ese registro a 8.1 h con la `version` vigente
- **THEN** (Entonces) la respuesta es 422 con `code` = `daily-hours-exceeded`
- **AND** `alreadyLoggedHours` = 16 y `limitHours` = 24

#### Scenario: Mover el registro a otro día lleno

- **GIVEN** (Dado) un registro `PENDING` de 5 h el 2026-11-05 y 20 h ya registradas por el mismo abogado el 2026-11-04
- **WHEN** (Cuando) edita el registro cambiando `workDate` a 2026-11-04
- **THEN** (Entonces) la respuesta es 422 con `code` = `daily-hours-exceeded` y `alreadyLoggedHours` = 20

#### Scenario: Edición con fecha futura

- **GIVEN** (Dado) un registro `PENDING` editable y hoy es 2026-11-07 en `America/La_Paz`
- **WHEN** (Cuando) el autor cambia `workDate` a 2026-11-08
- **THEN** (Entonces) la respuesta es 422 con `code` = `future-date`

#### Scenario: Edición hacia un asunto cerrado

- **GIVEN** (Dado) un registro `PENDING` editable y un asunto `CLOSED`
- **WHEN** (Cuando) el autor cambia `matterId` al asunto cerrado
- **THEN** (Entonces) la respuesta es 422 con `code` = `matter-closed`

#### Scenario: El asunto original se cerró después de la carga

- **GIVEN** (Dado) un registro `PENDING` editable cuyo asunto pasó a `CLOSED` después de crearse el registro
- **WHEN** (Cuando) el autor edita solo `hours`, sin cambiar `matterId`, con la `version` vigente
- **THEN** (Entonces) la respuesta es 422 con `code` = `matter-closed`
- **AND** el registro conserva sus valores previos

#### Scenario: Edición con incremento inválido

- **GIVEN** (Dado) un registro `PENDING` editable
- **WHEN** (Cuando) el autor cambia `hours` a 2.05
- **THEN** (Entonces) la respuesta es 422 con `code` = `hours-invalid-increment`

#### Scenario: Edición fuera de rango

- **GIVEN** (Dado) un registro `PENDING` editable
- **WHEN** (Cuando) el autor cambia `hours` a 0
- **THEN** (Entonces) la respuesta es 422 con `code` = `hours-out-of-range`

### Requirement: Control de concurrencia optimista en la edición

El sistema SHALL (DEBE) exigir la `version` vigente del registro en `PUT /api/time-entries/{id}` y SHALL (DEBE) responder 409 `concurrent-modification` cuando la `version` enviada no coincida con la almacenada, sin aplicar ningún cambio. Si falta `version`, el sistema SHALL (DEBE) responder 400 `validation-error`.

#### Scenario: Versión obsoleta

- **GIVEN** (Dado) un registro `PENDING` con `version` = 1 en el servidor
- **WHEN** (Cuando) el autor envía `PUT /api/time-entries/{id}` con `version` = 0
- **THEN** (Entonces) la respuesta es 409 con `code` = `concurrent-modification`
- **AND** el registro conserva sus valores previos

#### Scenario: Versión ausente

- **GIVEN** (Dado) un registro `PENDING` editable
- **WHEN** (Cuando) el autor envía `PUT /api/time-entries/{id}` sin `version`
- **THEN** (Entonces) la respuesta es 400 con `code` = `validation-error` y `errors[].field` = `version`

### Requirement: Aprobación de registros por un socio

El sistema SHALL (DEBE) permitir que un usuario `PARTNER` apruebe un registro `PENDING` mediante `POST /api/time-entries/{id}/approve`, respondiendo 200 con el registro en estado `APPROVED`. El sistema SHALL (DEBE) responder 409 `entry-not-pending` si el registro no está `PENDING`, 403 `forbidden` si quien invoca es `LAWYER` y 404 `not-found` si el registro no existe.

#### Scenario: Socio aprueba un registro pendiente

- **GIVEN** (Dado) un socio autenticado y un registro `PENDING`
- **WHEN** (Cuando) envía `POST /api/time-entries/{id}/approve`
- **THEN** (Entonces) la respuesta es 200 y el registro tiene `status` = `APPROVED`

#### Scenario: Aprobar un registro ya aprobado

- **GIVEN** (Dado) un socio autenticado y un registro `APPROVED`
- **WHEN** (Cuando) envía `POST /api/time-entries/{id}/approve`
- **THEN** (Entonces) la respuesta es 409 con `code` = `entry-not-pending`

#### Scenario: Abogado intenta aprobar

- **GIVEN** (Dado) un abogado autenticado y un registro `PENDING` propio
- **WHEN** (Cuando) envía `POST /api/time-entries/{id}/approve`
- **THEN** (Entonces) la respuesta es 403 con `code` = `forbidden`
- **AND** el registro sigue `PENDING`

#### Scenario: Aprobar un registro inexistente

- **GIVEN** (Dado) un socio autenticado
- **WHEN** (Cuando) envía `POST /api/time-entries/{id}/approve` con un `id` que no existe
- **THEN** (Entonces) la respuesta es 404 con `code` = `not-found`

### Requirement: Rechazo de registros con motivo obligatorio

El sistema SHALL (DEBE) permitir que un usuario `PARTNER` rechace un registro `PENDING` mediante `POST /api/time-entries/{id}/reject` con `reason` de 1 a 500 caracteres (sin espacios al inicio y al final), respondiendo 200 con el registro en estado `REJECTED` y su `rejectionReason`. El sistema SHALL (DEBE) responder 422 `rejection-reason-required` si `reason` falta, está en blanco o supera 500 caracteres; 409 `entry-not-pending` si el registro no está `PENDING`; y 403 `forbidden` si quien invoca es `LAWYER`.

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

### Requirement: Estados terminales

El sistema SHALL NOT (NO DEBE) permitir ninguna transición de estado desde `APPROVED` o `REJECTED`; toda operación de edición, aprobación o rechazo sobre un registro en estado terminal SHALL (DEBE) responder 409 `entry-not-pending` y dejar el registro sin cambios.

#### Scenario: Rechazar un registro aprobado

- **GIVEN** (Dado) un socio autenticado y un registro `APPROVED`
- **WHEN** (Cuando) envía `POST /api/time-entries/{id}/reject` con un motivo válido
- **THEN** (Entonces) la respuesta es 409 con `code` = `entry-not-pending`
- **AND** el registro sigue `APPROVED`

#### Scenario: Aprobar un registro rechazado

- **GIVEN** (Dado) un socio autenticado y un registro `REJECTED`
- **WHEN** (Cuando) envía `POST /api/time-entries/{id}/approve`
- **THEN** (Entonces) la respuesta es 409 con `code` = `entry-not-pending`
- **AND** el registro sigue `REJECTED`

### Requirement: Listado de registros con filtros y paginación

El sistema SHALL (DEBE) exponer `GET /api/time-entries` con filtros opcionales `matterId`, `lawyerId`, `status`, `from` y `to` (rango inclusivo sobre `workDate`) y paginación `page` (base 0, por defecto 0) y `size` (por defecto 20, máximo 100), respondiendo 200 con `{items, page, size, totalItems, totalPages}`. Para un `PARTNER` SHALL (DEBE) devolver registros de todos los abogados que cumplan los filtros. Para un `LAWYER` SHALL (DEBE) devolver solo sus propios registros y SHALL (DEBE) ignorar `lawyerId`. El parámetro opcional `sort` SHALL (DEBE) aceptar solo `workDate,desc` (valor por defecto: `workDate` descendente y, a igual fecha, `createdAt` descendente) y `createdAt,asc` (`createdAt` ascendente). Cada elemento de `items` SHALL (DEBE) incluir `editableUntil`: la última fecha (en `America/La_Paz`) en que el registro puede editarse, igual a su fecha de creación en La Paz más 7 días. El sistema SHALL (DEBE) responder 400 `validation-error` si `size` es mayor que 100 o menor que 1, si `page` es negativo, si `status` o `sort` no son valores válidos o si `from` es posterior a `to`.

#### Scenario: Socio filtra pendientes de un abogado

- **GIVEN** (Dado) un socio autenticado y registros de varios abogados en distintos estados
- **WHEN** (Cuando) invoca `GET /api/time-entries?lawyerId={A}&status=PENDING`
- **THEN** (Entonces) la respuesta es 200 y todos los `items` son `PENDING` y del abogado A

#### Scenario: Socio filtra por asunto y rango de fechas

- **GIVEN** (Dado) un socio autenticado y registros del asunto M entre 2026-10-01 y 2026-11-07
- **WHEN** (Cuando) invoca `GET /api/time-entries?matterId={M}&from=2026-11-01&to=2026-11-05`
- **THEN** (Entonces) todos los `items` son del asunto M con `workDate` entre 2026-11-01 y 2026-11-05, ambos incluidos

#### Scenario: Abogado solo ve lo suyo

- **GIVEN** (Dado) un abogado A autenticado y registros de los abogados A y B
- **WHEN** (Cuando) invoca `GET /api/time-entries?lawyerId={B}`
- **THEN** (Entonces) la respuesta es 200 y todos los `items` pertenecen al abogado A

#### Scenario: Paginación por defecto

- **GIVEN** (Dado) un socio autenticado y 45 registros
- **WHEN** (Cuando) invoca `GET /api/time-entries` sin parámetros de paginación
- **THEN** (Entonces) la respuesta tiene `page` = 0, `size` = 20, 20 `items`, `totalItems` = 45 y `totalPages` = 3

#### Scenario: Tamaño de página excesivo

- **GIVEN** (Dado) un usuario autenticado
- **WHEN** (Cuando) invoca `GET /api/time-entries?size=101`
- **THEN** (Entonces) la respuesta es 400 con `code` = `validation-error` y `errors[].field` = `size`

#### Scenario: Rango de fechas invertido

- **GIVEN** (Dado) un usuario autenticado
- **WHEN** (Cuando) invoca `GET /api/time-entries?from=2026-11-05&to=2026-11-01`
- **THEN** (Entonces) la respuesta es 400 con `code` = `validation-error`

#### Scenario: Orden por defecto

- **GIVEN** (Dado) un abogado con registros de `workDate` 2026-11-03, 2026-11-05 y dos de 2026-11-04 creados en distinto momento
- **WHEN** (Cuando) invoca `GET /api/time-entries` sin `sort`
- **THEN** (Entonces) los `items` vienen en orden 2026-11-05, 2026-11-04 (el creado más tarde), 2026-11-04 (el creado antes), 2026-11-03

#### Scenario: Orden de la cola de aprobaciones

- **GIVEN** (Dado) un socio autenticado y tres registros `PENDING` creados en momentos distintos
- **WHEN** (Cuando) invoca `GET /api/time-entries?status=PENDING&sort=createdAt,asc`
- **THEN** (Entonces) los `items` vienen del más antiguo al más reciente por `createdAt`

#### Scenario: Orden no admitido

- **GIVEN** (Dado) un usuario autenticado
- **WHEN** (Cuando) invoca `GET /api/time-entries?sort=hours,desc`
- **THEN** (Entonces) la respuesta es 400 con `code` = `validation-error` y `errors[].field` = `sort`

#### Scenario: Fecha límite de edición en la respuesta

- **GIVEN** (Dado) un registro creado el 2026-10-31 (fecha en `America/La_Paz`)
- **WHEN** (Cuando) su autor invoca `GET /api/time-entries`
- **THEN** (Entonces) el elemento de ese registro tiene `editableUntil` = 2026-11-07

### Requirement: Pantalla "Registrar horas"

El sistema SHALL (DEBE) ofrecer al abogado un formulario "Registrar horas" con asunto (solo asuntos `OPEN`), fecha de trabajo (por defecto hoy en `America/La_Paz`), horas y descripción, que acepte coma o punto como separador decimal en las horas, y que tras un alta exitosa muestre una confirmación en español y limpie el formulario. La opción "Registrar horas" SHALL NOT (NO DEBE) mostrarse a un socio.

#### Scenario: Alta con coma decimal

- **GIVEN** (Dado) un abogado en la pantalla "Registrar horas"
- **WHEN** (Cuando) elige un asunto abierto, escribe "1,5" en horas, completa la descripción y pulsa "Guardar"
- **THEN** (Entonces) se crea un registro de 1,5 h en estado `PENDING`
- **AND** se muestra un mensaje de confirmación en español y el formulario queda limpio

#### Scenario: Error de límite diario visible

- **GIVEN** (Dado) un abogado con 20 h ya registradas el día elegido
- **WHEN** (Cuando) intenta registrar 5 h ese día
- **THEN** (Entonces) el formulario muestra en español que se supera el límite de 24 h, cuántas horas ya tiene y cuántas le quedan
- **AND** los datos ingresados se conservan para corregirlos

#### Scenario: Socio no ve la opción

- **GIVEN** (Dado) un socio autenticado
- **WHEN** (Cuando) abre la aplicación
- **THEN** (Entonces) el menú no muestra "Registrar horas"

### Requirement: Pantalla "Mis registros"

El sistema SHALL (DEBE) mostrar al abogado la lista paginada "Mis registros" con asunto, fecha de trabajo, horas (con coma decimal), descripción, estado y, si está rechazado, el motivo. La acción "Editar" SHALL (DEBE) aparecer solo en registros `PENDING` cuyo `editableUntil` (devuelto por el servidor) no sea anterior a la fecha de hoy en `America/La_Paz`, y SHALL NOT (NO DEBE) aparecer en registros `APPROVED`, `REJECTED` o con la ventana vencida.

#### Scenario: Editar un registro editable

- **GIVEN** (Dado) un abogado con un registro `PENDING` creado ayer
- **WHEN** (Cuando) pulsa "Editar", cambia las horas a "2,5" y guarda
- **THEN** (Entonces) la lista muestra el registro con 2,5 h y una confirmación en español

#### Scenario: Registros no editables

- **GIVEN** (Dado) un abogado con un registro `APPROVED`, uno `REJECTED` y uno `PENDING` creado hace 8 días
- **WHEN** (Cuando) abre "Mis registros"
- **THEN** (Entonces) ninguno de esos tres registros muestra la acción "Editar"
- **AND** el registro `REJECTED` muestra su motivo de rechazo

#### Scenario: Conflicto de concurrencia al editar

- **GIVEN** (Dado) un abogado editando un registro que cambió de versión o de estado en el servidor
- **WHEN** (Cuando) guarda la edición y recibe 409 (`concurrent-modification` o `entry-not-pending`)
- **THEN** (Entonces) se muestra en español que el registro cambió y se recarga la lista con los datos vigentes

### Requirement: Pantalla "Aprobaciones"

El sistema SHALL (DEBE) ofrecer al socio la cola "Aprobaciones" con los registros `PENDING` de todos los abogados (abogado, asunto, fecha, horas, descripción), con las acciones "Aprobar" y "Rechazar". "Rechazar" SHALL (DEBE) abrir un diálogo que exige un motivo de 1 a 500 caracteres; el botón de confirmación SHALL NOT (NO DEBE) estar habilitado mientras el motivo esté vacío. Tras aprobar o rechazar, el registro SHALL (DEBE) desaparecer de la cola. La opción "Aprobaciones" SHALL NOT (NO DEBE) mostrarse a un abogado.

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

### Requirement: Mensajes de error claros en español

El sistema SHALL (DEBE) mostrar en la UI cada error de la API como un mensaje claro en español asociado a su `code`, y los errores de validación (`errors[]`) junto al campo correspondiente. La UI SHALL NOT (NO DEBE) mostrar al usuario códigos técnicos, trazas ni textos en inglés.

#### Scenario: Error de fecha futura

- **GIVEN** (Dado) un abogado en "Registrar horas"
- **WHEN** (Cuando) el servidor responde 422 `future-date`
- **THEN** (Entonces) junto al campo fecha se muestra un mensaje en español que indica que no se pueden registrar horas en fechas futuras

#### Scenario: Error de incremento de horas

- **GIVEN** (Dado) un abogado en "Registrar horas"
- **WHEN** (Cuando) escribe "1,25" en horas y guarda
- **THEN** (Entonces) junto al campo horas se muestra en español que las horas deben ir en incrementos de 0,1
- **AND** no se muestra el slug `hours-invalid-increment`

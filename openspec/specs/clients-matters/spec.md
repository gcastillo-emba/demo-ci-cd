# clients-matters Specification

## Purpose

Gestionar el maestro de clientes y asuntos de la firma: el Socio da de alta clientes y asuntos y cierra asuntos de forma irreversible; todo usuario autenticado puede consultarlos con filtros y paginación, a través de la API `/api` y de las pantallas Clientes y Asuntos.

## Requirements

### Requirement: Autenticación obligatoria en clientes y asuntos

El sistema SHALL (DEBE) exigir un token Bearer válido en todos los endpoints `/api/clients**` y `/api/matters**`. Una petición sin token, con token inválido o con token expirado SHALL (DEBE) responder `401` con Problem Details `code` = `unauthenticated` y SHALL NOT (NO DEBE) devolver ni modificar datos.

#### Scenario: Listado de clientes sin token

- **GIVEN** (Dado) una petición sin cabecera `Authorization`
- **WHEN** (Cuando) se invoca `GET /api/clients`
- **THEN** (Entonces) la respuesta es `401` con `Content-Type: application/problem+json`
- **AND** el cuerpo tiene `code` = `unauthenticated`

#### Scenario: Creación de asunto con token expirado

- **GIVEN** (Dado) un token JWT de Socio cuya expiración ya pasó
- **WHEN** (Cuando) se invoca `POST /api/matters` con un cuerpo válido
- **THEN** (Entonces) la respuesta es `401` con `code` = `unauthenticated`
- **AND** no se crea ningún asunto

### Requirement: Errores en formato Problem Details

El sistema SHALL (DEBE) responder todo error de esta capacidad con `Content-Type: application/problem+json` (RFC 9457) y los campos `type` (`https://asuntos-horas.example/problems/{code}`), `title`, `status`, `detail` en español, `code` (slug estable en inglés) y `traceId`. Los errores de validación SHALL (DEBE) incluir además `errors[]` con un elemento `{field, message}` por cada campo inválido.

#### Scenario: Estructura de un error de validación

- **GIVEN** (Dado) un Socio autenticado
- **WHEN** (Cuando) envía `POST /api/clients` con `name` vacío
- **THEN** (Entonces) la respuesta es `400` con `type` = `https://asuntos-horas.example/problems/validation-error`
- **AND** el cuerpo contiene `title`, `status` = 400, `detail` en español, `code` = `validation-error` y un `traceId` no vacío
- **AND** `errors[]` contiene un elemento con `field` = `name`

### Requirement: Errores sin detalles internos

Las respuestas Problem Details de toda la API `/api` SHALL NOT (NO DEBEN) incluir trazas de pila, nombres de clases de excepción, sentencias SQL ni mensajes internos de librerías o de la base de datos. Un error inesperado (sin entrada en `ProblemCatalog`) SHALL (DEBE) responder `500` con `code` = `internal-error`, un `detail` genérico en español que no revele la causa y el `traceId` para correlacionarlo con los logs. Este comportamiento es compartido: C2–C4 lo heredan sin redeclararlo.

#### Scenario: Error inesperado de persistencia

- **GIVEN** (Dado) un Socio autenticado y una falla inesperada de la base de datos al crear un cliente
- **WHEN** (Cuando) envía `POST /api/clients` con un cuerpo válido
- **THEN** (Entonces) la respuesta es `500` con `Content-Type: application/problem+json`, `code` = `internal-error`, `type` = `https://asuntos-horas.example/problems/internal-error` y un `traceId` no vacío
- **AND** `detail` es un texto genérico en español
- **AND** el cuerpo no contiene trazas de pila, nombres de clases Java (p. ej. `SQLException`), texto SQL ni el mensaje original de la excepción

### Requirement: El Socio crea clientes

El sistema SHALL (DEBE) permitir a un usuario con rol `PARTNER` crear un cliente mediante `POST /api/clients` con `name` obligatorio (1 a 200 caracteres tras recortar espacios) y `taxId` opcional. La respuesta exitosa SHALL (DEBE) ser `201` con el cliente creado (`id`, `name`, `taxId`, `createdAt`) y la cabecera `Location` apuntando a `/api/clients/{clientId}`.

#### Scenario: Alta de cliente con identificación tributaria

- **GIVEN** (Dado) un Socio autenticado y ningún cliente con `taxId` = `0000000001`
- **WHEN** (Cuando) envía `POST /api/clients` con `name` = `Inversiones Andinas S.A.` y `taxId` = `0000000001`
- **THEN** (Entonces) la respuesta es `201` con el cliente creado y un `id` asignado
- **AND** la cabecera `Location` es `/api/clients/{id}`

#### Scenario: Alta de cliente sin identificación tributaria

- **GIVEN** (Dado) un Socio autenticado
- **WHEN** (Cuando) envía `POST /api/clients` solo con `name` = `Comercial Illimani`
- **THEN** (Entonces) la respuesta es `201` y el cliente tiene `taxId` nulo

### Requirement: Validación de datos del cliente

El sistema SHALL (DEBE) rechazar con `400` y `code` = `validation-error` la creación de un cliente cuyo `name` falte, quede vacío tras recortar espacios o supere 200 caracteres, o cuyo `taxId` informado esté vacío o supere 50 caracteres. El sistema SHALL NOT (NO DEBE) crear el cliente en esos casos.

#### Scenario: Nombre ausente

- **GIVEN** (Dado) un Socio autenticado
- **WHEN** (Cuando) envía `POST /api/clients` sin el campo `name`
- **THEN** (Entonces) la respuesta es `400` con `code` = `validation-error`
- **AND** `errors[]` contiene `field` = `name`

#### Scenario: Nombre de más de 200 caracteres

- **GIVEN** (Dado) un Socio autenticado
- **WHEN** (Cuando) envía `POST /api/clients` con un `name` de 201 caracteres
- **THEN** (Entonces) la respuesta es `400` con `code` = `validation-error` y `errors[]` contiene `field` = `name`
- **AND** no se crea ningún cliente

#### Scenario: Nombre de exactamente 200 caracteres

- **GIVEN** (Dado) un Socio autenticado
- **WHEN** (Cuando) envía `POST /api/clients` con un `name` de 200 caracteres
- **THEN** (Entonces) la respuesta es `201`

#### Scenario: taxId de más de 50 caracteres

- **GIVEN** (Dado) un Socio autenticado
- **WHEN** (Cuando) envía `POST /api/clients` con un `name` válido y un `taxId` de 51 caracteres
- **THEN** (Entonces) la respuesta es `400` con `code` = `validation-error` y `errors[]` contiene `field` = `taxId`
- **AND** no se crea ningún cliente

#### Scenario: taxId vacío

- **GIVEN** (Dado) un Socio autenticado
- **WHEN** (Cuando) envía `POST /api/clients` con un `name` válido y `taxId` = `"   "` (solo espacios)
- **THEN** (Entonces) la respuesta es `400` con `code` = `validation-error` y `errors[]` contiene `field` = `taxId`
- **AND** no se crea ningún cliente

### Requirement: Identificación tributaria única

El sistema SHALL (DEBE) garantizar que no existan dos clientes con el mismo `taxId`. Un alta con un `taxId` ya registrado SHALL (DEBE) responder `409` con `code` = `client-tax-id-taken` y SHALL NOT (NO DEBE) crear el cliente. Varios clientes sin `taxId` SHALL (DEBE) poder coexistir.

#### Scenario: taxId duplicado

- **GIVEN** (Dado) un cliente existente con `taxId` = `0000000001`
- **WHEN** (Cuando) un Socio envía `POST /api/clients` con otro `name` y `taxId` = `0000000001`
- **THEN** (Entonces) la respuesta es `409` con `code` = `client-tax-id-taken`
- **AND** el número total de clientes no cambia

#### Scenario: Varios clientes sin taxId

- **GIVEN** (Dado) un cliente existente sin `taxId`
- **WHEN** (Cuando) un Socio crea otro cliente sin `taxId`
- **THEN** (Entonces) la respuesta es `201`

### Requirement: Listado paginado de clientes con filtro por nombre

El sistema SHALL (DEBE) listar clientes con `GET /api/clients` para cualquier usuario autenticado (`PARTNER` o `LAWYER`), aceptando el filtro opcional `q` (coincidencia parcial sin distinguir mayúsculas ni minúsculas sobre `name`), ordenados por `name` ascendente y envueltos en `{items, page, size, totalItems, totalPages}`.

#### Scenario: Abogado lista clientes filtrando por nombre

- **GIVEN** (Dado) los clientes `Inversiones Andinas S.A.`, `Andes Logística` y `Comercial Illimani`
- **WHEN** (Cuando) un Abogado autenticado invoca `GET /api/clients?q=andin`
- **THEN** (Entonces) la respuesta es `200` e `items` contiene solo `Inversiones Andinas S.A.`
- **AND** `totalItems` = 1

#### Scenario: Listado sin filtro

- **GIVEN** (Dado) 3 clientes registrados
- **WHEN** (Cuando) un Socio invoca `GET /api/clients`
- **THEN** (Entonces) la respuesta es `200` con los 3 clientes ordenados por `name`, `page` = 0 y `size` = 20

### Requirement: Paginación de listados

El sistema SHALL (DEBE) aceptar en `GET /api/clients` y `GET /api/matters` los parámetros `page` (base 0, por defecto 0) y `size` (por defecto 20, máximo 100) y devolver `page`, `size`, `totalItems` y `totalPages` coherentes con el resultado. Un `page` negativo, un `size` menor que 1 o mayor que 100, o un valor no numérico SHALL (DEBE) responder `400` con `code` = `validation-error`. Una página más allá de la última SHALL (DEBE) responder `200` con `items` vacío.

#### Scenario: Segunda página de asuntos

- **GIVEN** (Dado) 25 asuntos registrados
- **WHEN** (Cuando) un usuario autenticado invoca `GET /api/matters?page=1&size=10`
- **THEN** (Entonces) la respuesta es `200` con 10 elementos en `items`
- **AND** `page` = 1, `size` = 10, `totalItems` = 25 y `totalPages` = 3

#### Scenario: Tamaño de página mayor al máximo

- **GIVEN** (Dado) un usuario autenticado
- **WHEN** (Cuando) invoca `GET /api/clients?size=101`
- **THEN** (Entonces) la respuesta es `400` con `code` = `validation-error` y `errors[]` contiene `field` = `size`

#### Scenario: Página negativa

- **GIVEN** (Dado) un usuario autenticado
- **WHEN** (Cuando) invoca `GET /api/matters?page=-1`
- **THEN** (Entonces) la respuesta es `400` con `code` = `validation-error` y `errors[]` contiene `field` = `page`

#### Scenario: size no numérico

- **GIVEN** (Dado) un usuario autenticado
- **WHEN** (Cuando) invoca `GET /api/clients?size=abc`
- **THEN** (Entonces) la respuesta es `400` con `code` = `validation-error` y `errors[]` contiene `field` = `size`

#### Scenario: Página fuera de rango

- **GIVEN** (Dado) 3 clientes registrados
- **WHEN** (Cuando) un usuario autenticado invoca `GET /api/clients?page=5`
- **THEN** (Entonces) la respuesta es `200` con `items` vacío y `totalItems` = 3

### Requirement: Consulta de cliente por id

El sistema SHALL (DEBE) devolver un cliente con `GET /api/clients/{clientId}` a cualquier usuario autenticado. Si el cliente no existe, SHALL (DEBE) responder `404` con `code` = `not-found`.

#### Scenario: Cliente existente

- **GIVEN** (Dado) un cliente existente con id `C`
- **WHEN** (Cuando) un Abogado invoca `GET /api/clients/C`
- **THEN** (Entonces) la respuesta es `200` con `id`, `name`, `taxId` y `createdAt` del cliente

#### Scenario: Cliente inexistente

- **GIVEN** (Dado) un id que no corresponde a ningún cliente
- **WHEN** (Cuando) un Socio invoca `GET /api/clients/{clientId}`
- **THEN** (Entonces) la respuesta es `404` con `code` = `not-found`

### Requirement: El Socio crea asuntos

El sistema SHALL (DEBE) permitir a un usuario con rol `PARTNER` crear un asunto mediante `POST /api/matters` con `clientId` de un cliente existente, `title` obligatorio y `description` opcional. La respuesta SHALL (DEBE) ser `201` con el asunto (`id`, `code`, `clientId`, `clientName`, `title`, `description`, `status` = `OPEN`, `createdAt`, `closedAt` nulo) y la cabecera `Location` apuntando a `/api/matters/{matterId}`. El cliente SHALL NOT (NO DEBE) poder fijar `code`, `status` ni `closedAt`.

#### Scenario: Alta de asunto

- **GIVEN** (Dado) un Socio autenticado y un cliente existente con id `C`
- **WHEN** (Cuando) envía `POST /api/matters` con `clientId` = `C` y `title` = `Due diligence adquisición planta`
- **THEN** (Entonces) la respuesta es `201` con `status` = `OPEN`, `closedAt` nulo y un `code` con formato `AS-YYYY-NNNN`
- **AND** la cabecera `Location` es `/api/matters/{id}`

#### Scenario: Campos de solo lectura ignorados

- **GIVEN** (Dado) un Socio autenticado y un cliente existente con id `C`
- **WHEN** (Cuando) envía `POST /api/matters` con `clientId` = `C`, un `title` válido, `status` = `CLOSED` y `code` = `AS-1999-0001`
- **THEN** (Entonces) la respuesta es `201` con `status` = `OPEN` y un `code` generado por el servidor distinto de `AS-1999-0001`

#### Scenario: Cliente del asunto inexistente

- **GIVEN** (Dado) un Socio autenticado y un `clientId` que no corresponde a ningún cliente
- **WHEN** (Cuando) envía `POST /api/matters` con ese `clientId` y un `title` válido
- **THEN** (Entonces) la respuesta es `404` con `code` = `not-found`
- **AND** no se crea ningún asunto

### Requirement: Validación de datos del asunto

El sistema SHALL (DEBE) rechazar con `400` y `code` = `validation-error` la creación de un asunto si falta `clientId`, si `title` falta o, tras recortar espacios, queda vacío o supera 200 caracteres, o si `description` supera 2000 caracteres. El sistema SHALL NOT (NO DEBE) crear el asunto en esos casos.

#### Scenario: Título ausente y clientId ausente

- **GIVEN** (Dado) un Socio autenticado
- **WHEN** (Cuando) envía `POST /api/matters` con cuerpo `{}`
- **THEN** (Entonces) la respuesta es `400` con `code` = `validation-error`
- **AND** `errors[]` contiene `field` = `clientId` y `field` = `title`

#### Scenario: Descripción de más de 2000 caracteres

- **GIVEN** (Dado) un Socio autenticado y un cliente existente con id `C`
- **WHEN** (Cuando) envía `POST /api/matters` con `clientId` = `C`, un `title` válido y una `description` de 2001 caracteres
- **THEN** (Entonces) la respuesta es `400` con `code` = `validation-error` y `errors[]` contiene `field` = `description`

#### Scenario: Título de más de 200 caracteres

- **GIVEN** (Dado) un Socio autenticado y un cliente existente con id `C`
- **WHEN** (Cuando) envía `POST /api/matters` con `clientId` = `C` y un `title` de 201 caracteres
- **THEN** (Entonces) la respuesta es `400` con `code` = `validation-error` y `errors[]` contiene `field` = `title`

### Requirement: Código de asunto generado por el servidor

El sistema SHALL (DEBE) asignar a cada asunto nuevo un `code` único con formato `AS-YYYY-NNNN`, donde `YYYY` es el año de la fecha de creación en la zona horaria de la firma (`America/La_Paz`) y `NNNN` es un correlativo de 4 dígitos con ceros a la izquierda que empieza en `0001` cada año y crece de uno en uno. Dos asuntos SHALL NOT (NO DEBE) compartir `code`, aun si se crean de forma concurrente.

#### Scenario: Primer asunto del año

- **GIVEN** (Dado) la fecha actual en `America/La_Paz` es 2026-11-07 y no existe ningún asunto de 2026
- **WHEN** (Cuando) un Socio crea un asunto
- **THEN** (Entonces) el asunto recibe `code` = `AS-2026-0001`

#### Scenario: Correlativo siguiente

- **GIVEN** (Dado) el último asunto de 2026 tiene `code` = `AS-2026-0041`
- **WHEN** (Cuando) un Socio crea otro asunto en 2026
- **THEN** (Entonces) el asunto recibe `code` = `AS-2026-0042`

#### Scenario: Cambio de año según la zona de la firma

- **GIVEN** (Dado) el instante actual es 2027-01-01T02:00Z (31 de diciembre de 2026, 22:00 en `America/La_Paz`) y el último asunto de 2026 es `AS-2026-0042`
- **WHEN** (Cuando) un Socio crea un asunto
- **THEN** (Entonces) el asunto recibe `code` = `AS-2026-0043`

#### Scenario: Altas concurrentes

- **GIVEN** (Dado) dos Socios autenticados
- **WHEN** (Cuando) ambos crean un asunto al mismo tiempo
- **THEN** (Entonces) ambas respuestas son `201` y los dos `code` son distintos

### Requirement: Listado paginado de asuntos con filtros

El sistema SHALL (DEBE) listar asuntos con `GET /api/matters` para cualquier usuario autenticado, aceptando los filtros opcionales combinables `clientId`, `status` (`OPEN` o `CLOSED`) y `q` (coincidencia parcial sin distinguir mayúsculas ni minúsculas sobre `title` o `code`), ordenados por `code` descendente y envueltos en el formato de página. Un `status` distinto de `OPEN` o `CLOSED` SHALL (DEBE) responder `400` con `code` = `validation-error`.

#### Scenario: Filtrar por cliente y estado

- **GIVEN** (Dado) el cliente `C` con un asunto `OPEN` y otro `CLOSED`, y otro cliente con un asunto `OPEN`
- **WHEN** (Cuando) un Abogado invoca `GET /api/matters?clientId=C&status=OPEN`
- **THEN** (Entonces) la respuesta es `200` e `items` contiene solo el asunto `OPEN` del cliente `C`

#### Scenario: Buscar por código

- **GIVEN** (Dado) los asuntos `AS-2026-0007` y `AS-2026-0012`
- **WHEN** (Cuando) un Socio invoca `GET /api/matters?q=0012`
- **THEN** (Entonces) `items` contiene solo `AS-2026-0012`

#### Scenario: Buscar por título

- **GIVEN** (Dado) un asunto con `title` = `Due diligence adquisición planta`
- **WHEN** (Cuando) un usuario autenticado invoca `GET /api/matters?q=DILIGENCE`
- **THEN** (Entonces) `items` contiene ese asunto

#### Scenario: Estado inválido

- **GIVEN** (Dado) un usuario autenticado
- **WHEN** (Cuando) invoca `GET /api/matters?status=ARCHIVED`
- **THEN** (Entonces) la respuesta es `400` con `code` = `validation-error` y `errors[]` contiene `field` = `status`

### Requirement: Consulta de asunto por id

El sistema SHALL (DEBE) devolver un asunto con `GET /api/matters/{matterId}` a cualquier usuario autenticado. Si el asunto no existe, SHALL (DEBE) responder `404` con `code` = `not-found`.

#### Scenario: Asunto existente

- **GIVEN** (Dado) un asunto existente con id `M`
- **WHEN** (Cuando) un Abogado invoca `GET /api/matters/M`
- **THEN** (Entonces) la respuesta es `200` con `code`, `clientId`, `clientName`, `title`, `description`, `status`, `createdAt` y `closedAt` del asunto

#### Scenario: Asunto inexistente

- **GIVEN** (Dado) un id que no corresponde a ningún asunto
- **WHEN** (Cuando) un Socio invoca `GET /api/matters/{matterId}`
- **THEN** (Entonces) la respuesta es `404` con `code` = `not-found`

### Requirement: El Socio cierra asuntos

El sistema SHALL (DEBE) permitir a un usuario `PARTNER` cerrar un asunto `OPEN` mediante `POST /api/matters/{matterId}/close`, respondiendo `200` con el asunto en `status` = `CLOSED` y `closedAt` igual al instante del cierre. El cierre SHALL NOT (NO DEBE) depender de la existencia ni del estado de registros de horas del asunto; en particular, SHALL (DEBE) permitirse aunque tenga registros de horas pendientes. Si el asunto no existe, SHALL (DEBE) responder `404` con `code` = `not-found`.

#### Scenario: Cierre exitoso

- **GIVEN** (Dado) un Socio autenticado y un asunto `OPEN` con id `M`
- **WHEN** (Cuando) invoca `POST /api/matters/M/close`
- **THEN** (Entonces) la respuesta es `200` con `status` = `CLOSED` y `closedAt` no nulo
- **AND** un `GET /api/matters/M` posterior devuelve `status` = `CLOSED` con el mismo `closedAt`

#### Scenario: El cierre no impone condiciones sobre registros de horas

- **GIVEN** (Dado) un Socio autenticado y un asunto `OPEN` con id `M`
- **WHEN** (Cuando) invoca `POST /api/matters/M/close` sin cuerpo
- **THEN** (Entonces) la respuesta es `200` con `status` = `CLOSED`
- **AND** ni la petición ni la respuesta exigen ni contienen información sobre registros de horas

#### Scenario: Cierre de asunto inexistente

- **GIVEN** (Dado) un id que no corresponde a ningún asunto
- **WHEN** (Cuando) un Socio invoca `POST /api/matters/{id}/close`
- **THEN** (Entonces) la respuesta es `404` con `code` = `not-found`

### Requirement: Un asunto cerrado no se vuelve a cerrar

El sistema SHALL (DEBE) responder `409` con `code` = `matter-already-closed` al intentar cerrar un asunto que ya está `CLOSED`, y SHALL NOT (NO DEBE) modificar su `closedAt`.

#### Scenario: Cerrar dos veces

- **GIVEN** (Dado) un asunto con id `M` cerrado con `closedAt` = T1
- **WHEN** (Cuando) un Socio invoca `POST /api/matters/M/close`
- **THEN** (Entonces) la respuesta es `409` con `code` = `matter-already-closed`
- **AND** un `GET /api/matters/M` posterior sigue devolviendo `closedAt` = T1

### Requirement: Un asunto cerrado no se reabre

El sistema SHALL NOT (NO DEBE) ofrecer ninguna operación, en la API ni en la interfaz, que devuelva un asunto `CLOSED` al estado `OPEN` ni que modifique un asunto existente. Una petición autenticada a `/api/matters/{matterId}` o `/api/clients/{clientId}` con un método no ofrecido (`PUT`, `PATCH`, `DELETE`) SHALL (DEBE) responder `405` con Problem Details `code` = `method-not-allowed`, cualquiera sea el rol (también un `LAWYER`), y el recurso SHALL (DEBE) permanecer sin cambios.

#### Scenario: Intento de reabrir con PATCH

- **GIVEN** (Dado) un asunto `CLOSED` con id `M`
- **WHEN** (Cuando) un Socio envía `PATCH /api/matters/M` con `status` = `OPEN`
- **THEN** (Entonces) la respuesta es `405` con `Content-Type: application/problem+json` y `code` = `method-not-allowed`
- **AND** un `GET /api/matters/M` posterior devuelve `status` = `CLOSED`

#### Scenario: Abogado envía un método no ofrecido

- **GIVEN** (Dado) un Abogado autenticado y un asunto con id `M`
- **WHEN** (Cuando) envía `PATCH /api/matters/M`, `PUT /api/matters/M` o `DELETE /api/matters/M`
- **THEN** (Entonces) la respuesta es `405` con `code` = `method-not-allowed` (no `403`): la resolución del método precede a la autorización por rol porque no existe ninguna ruta para ese método, así que la precedencia `403 > 400` no aplica
- **AND** el asunto permanece sin cambios

#### Scenario: La interfaz no ofrece reabrir

- **GIVEN** (Dado) un Socio en la pantalla Asuntos
- **WHEN** (Cuando) visualiza un asunto `CLOSED`
- **THEN** (Entonces) no se muestra ninguna acción para reabrirlo ni para editarlo

### Requirement: El Abogado solo puede leer

El sistema SHALL (DEBE) permitir al rol `LAWYER` solo operaciones de lectura (`GET`) sobre clientes y asuntos. Cualquier escritura de un `LAWYER` (`POST` a `/api/clients`, `/api/matters` o `/api/matters/{matterId}/close`) SHALL (DEBE) responder `403` con `code` = `forbidden` y SHALL NOT (NO DEBE) producir cambios. Si una petición incumple varias reglas, SHALL (DEBE) responderse según esta precedencia: `401 unauthenticated` > `403 forbidden` > `400 validation-error` > `404 not-found` > `409`.

#### Scenario: Abogado intenta crear un cliente

- **GIVEN** (Dado) un Abogado autenticado
- **WHEN** (Cuando) envía `POST /api/clients` con un cuerpo válido
- **THEN** (Entonces) la respuesta es `403` con `code` = `forbidden`
- **AND** no se crea ningún cliente

#### Scenario: Abogado intenta crear un asunto

- **GIVEN** (Dado) un Abogado autenticado y un cliente existente con id `C`
- **WHEN** (Cuando) envía `POST /api/matters` con `clientId` = `C` y un `title` válido
- **THEN** (Entonces) la respuesta es `403` con `code` = `forbidden`
- **AND** no se crea ningún asunto

#### Scenario: Abogado intenta cerrar un asunto

- **GIVEN** (Dado) un Abogado autenticado y un asunto `OPEN` con id `M`
- **WHEN** (Cuando) invoca `POST /api/matters/M/close`
- **THEN** (Entonces) la respuesta es `403` con `code` = `forbidden`
- **AND** el asunto sigue `OPEN`

#### Scenario: Abogado cierra un asunto inexistente

- **GIVEN** (Dado) un Abogado autenticado y un id que no corresponde a ningún asunto
- **WHEN** (Cuando) invoca `POST /api/matters/{id}/close`
- **THEN** (Entonces) la respuesta es `403` con `code` = `forbidden` (no `404`)

#### Scenario: Abogado envía un cuerpo inválido

- **GIVEN** (Dado) un Abogado autenticado
- **WHEN** (Cuando) envía `POST /api/clients` con cuerpo `{}`
- **THEN** (Entonces) la respuesta es `403` con `code` = `forbidden` (no `400`)
- **AND** no se crea ningún cliente

#### Scenario: Cuerpo inválido con cliente inexistente

- **GIVEN** (Dado) un Socio autenticado y un `clientId` que no corresponde a ningún cliente
- **WHEN** (Cuando) envía `POST /api/matters` con ese `clientId` y sin `title`
- **THEN** (Entonces) la respuesta es `400` con `code` = `validation-error` (no `404`) y `errors[]` contiene `field` = `title`

### Requirement: Pantalla Clientes

La interfaz SHALL (DEBE) ofrecer, a todo usuario autenticado, una pantalla **Clientes** en español que muestre la lista paginada de clientes (nombre e identificación tributaria), un campo de búsqueda por nombre y controles de paginación (anterior / siguiente con indicador de página). Cuando no haya resultados SHALL (DEBE) mostrar un mensaje de lista vacía.

#### Scenario: Buscar un cliente

- **GIVEN** (Dado) un Abogado en la pantalla Clientes con los clientes `Inversiones Andinas S.A.` y `Comercial Illimani`
- **WHEN** (Cuando) escribe `andin` en el buscador
- **THEN** (Entonces) la lista muestra solo `Inversiones Andinas S.A.`

#### Scenario: Navegar a la página siguiente

- **GIVEN** (Dado) 25 clientes registrados y un usuario en la pantalla Clientes viendo la página 1
- **WHEN** (Cuando) pulsa **Siguiente**
- **THEN** (Entonces) la lista muestra los clientes 21 a 25 y el indicador de página muestra la página 2 de 2

#### Scenario: Búsqueda sin resultados

- **GIVEN** (Dado) un usuario en la pantalla Clientes
- **WHEN** (Cuando) busca un texto que no coincide con ningún cliente
- **THEN** (Entonces) se muestra el mensaje `No se encontraron clientes`

### Requirement: Pantalla Asuntos

La interfaz SHALL (DEBE) ofrecer, a todo usuario autenticado, una pantalla **Asuntos** en español que muestre la lista paginada de asuntos (código, título, cliente, estado en español `Abierto` / `Cerrado`), con filtros por cliente, por estado (Todos, Abierto, Cerrado) y búsqueda por título o código, y controles de paginación.

#### Scenario: Filtrar asuntos abiertos de un cliente

- **GIVEN** (Dado) un usuario en la pantalla Asuntos
- **WHEN** (Cuando) selecciona el cliente `Inversiones Andinas S.A.` y el estado `Abierto`
- **THEN** (Entonces) la lista muestra solo los asuntos abiertos de ese cliente, con el estado `Abierto`

#### Scenario: Buscar por código

- **GIVEN** (Dado) un usuario en la pantalla Asuntos
- **WHEN** (Cuando) escribe `AS-2026-0012` en el buscador
- **THEN** (Entonces) la lista muestra solo el asunto `AS-2026-0012`

### Requirement: Formularios de alta solo para el Socio

La interfaz SHALL (DEBE) mostrar al Socio los formularios **Nuevo cliente** (nombre, identificación tributaria) y **Nuevo asunto** (cliente, título, descripción), validar en el cliente los mismos límites de la API, mostrar los errores por campo y, al crear con éxito, mostrar el nuevo elemento en la lista. La interfaz SHALL NOT (NO DEBE) mostrar estos formularios ni sus botones de acceso a un Abogado.

#### Scenario: Socio crea un cliente desde la pantalla

- **GIVEN** (Dado) un Socio en la pantalla Clientes
- **WHEN** (Cuando) abre **Nuevo cliente**, escribe el nombre `Comercial Illimani` y guarda
- **THEN** (Entonces) el formulario se cierra y `Comercial Illimani` aparece en la lista

#### Scenario: Socio crea un asunto y ve su código

- **GIVEN** (Dado) un Socio en la pantalla Asuntos y un cliente existente
- **WHEN** (Cuando) abre **Nuevo asunto**, elige el cliente, escribe un título y guarda
- **THEN** (Entonces) el asunto aparece en la lista con su código `AS-YYYY-NNNN` y estado `Abierto`

#### Scenario: Error de validación en el formulario

- **GIVEN** (Dado) un Socio con el formulario **Nuevo asunto** abierto
- **WHEN** (Cuando) intenta guardar sin título
- **THEN** (Entonces) se muestra un mensaje de error junto al campo título y no se envía la petición

#### Scenario: Identificación tributaria duplicada en el formulario

- **GIVEN** (Dado) un cliente existente con `taxId` = `0000000001` y un Socio con el formulario **Nuevo cliente** abierto
- **WHEN** (Cuando) guarda con `taxId` = `0000000001` y la API responde `409 client-tax-id-taken`
- **THEN** (Entonces) el formulario sigue abierto y muestra un mensaje de identificación tributaria ya registrada junto a ese campo

#### Scenario: Abogado no ve formularios de alta

- **GIVEN** (Dado) un Abogado autenticado
- **WHEN** (Cuando) abre las pantallas Clientes y Asuntos
- **THEN** (Entonces) no se muestran los botones **Nuevo cliente** ni **Nuevo asunto**

### Requirement: Cierre de asunto con confirmación en la interfaz

La interfaz SHALL (DEBE) mostrar al Socio la acción **Cerrar asunto** solo en asuntos `Abierto` y SHALL (DEBE) pedir confirmación explícita, advirtiendo que el cierre es irreversible, antes de enviar la petición. Al confirmar, el asunto SHALL (DEBE) mostrarse como `Cerrado`; al cancelar, SHALL NOT (NO DEBE) enviarse ninguna petición. La acción SHALL NOT (NO DEBE) mostrarse a un Abogado ni en asuntos `Cerrado`.

#### Scenario: Socio confirma el cierre

- **GIVEN** (Dado) un Socio en la pantalla Asuntos con el asunto `AS-2026-0012` `Abierto`
- **WHEN** (Cuando) pulsa **Cerrar asunto** y confirma en el diálogo
- **THEN** (Entonces) el asunto se muestra como `Cerrado` y desaparece la acción **Cerrar asunto**

#### Scenario: Socio cancela el cierre

- **GIVEN** (Dado) un Socio en la pantalla Asuntos con un asunto `Abierto`
- **WHEN** (Cuando) pulsa **Cerrar asunto** y cancela en el diálogo
- **THEN** (Entonces) no se envía ninguna petición y el asunto sigue `Abierto`

#### Scenario: Asunto cerrado por otro Socio mientras tanto

- **GIVEN** (Dado) un Socio que ve un asunto como `Abierto` que otro Socio ya cerró
- **WHEN** (Cuando) pulsa **Cerrar asunto**, confirma y la API responde `409 matter-already-closed`
- **THEN** (Entonces) se muestra un mensaje indicando que el asunto ya estaba cerrado y el asunto pasa a mostrarse como `Cerrado`

#### Scenario: Abogado no ve la acción de cierre

- **GIVEN** (Dado) un Abogado en la pantalla Asuntos con asuntos `Abierto`
- **WHEN** (Cuando) visualiza la lista
- **THEN** (Entonces) no se muestra la acción **Cerrar asunto**

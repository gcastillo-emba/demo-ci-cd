## MODIFIED Requirements

### Requirement: Errores sin detalles internos

Las respuestas Problem Details de toda la API `/api` SHALL NOT (NO DEBEN) incluir trazas de pila, nombres de clases de excepción, sentencias SQL ni mensajes internos de librerías o de la base de datos. Un error inesperado (sin entrada en `ProblemCatalog`) SHALL (DEBE) responder `500` con `code` = `internal-error`, un `detail` genérico en español que no revele la causa y el `traceId` para correlacionarlo con los logs. Este comportamiento es compartido: C2–C4 lo heredan sin redeclararlo.

#### Scenario: Error inesperado de persistencia

- **GIVEN** (Dado) un Socio autenticado y una falla inesperada de la base de datos al crear un cliente
- **WHEN** (Cuando) envía `POST /api/clients` con un cuerpo válido
- **THEN** (Entonces) la respuesta es `500` con `Content-Type: application/problem+json`, `code` = `internal-error`, `type` = `https://asuntos-horas.example/problems/internal-error` y un `traceId` no vacío
- **AND** `detail` es un texto genérico en español
- **AND** el cuerpo no contiene trazas de pila, nombres de clases Java (p. ej. `SQLException`), texto SQL ni el mensaje original de la excepción

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

## Context

Ver `proposal.md` (Why). Restricciones vigentes que condicionan el diseño:

- Hexagonal + API-first (ADR-001): `api/openapi.yaml` es la única fuente; los controllers implementan las interfaces generadas por openapi-generator (`spring`, `interfaceOnly`) y `ContractConformanceIT` exige igualdad de endpoints. ArchUnit verifica capas, `Clock` obligatorio y que cada `DomainException` tenga entrada en `ProblemCatalog`.
- El walking skeleton (F3) ya aporta: `POST /api/auth/login`, `GET /api/me`, `GET /api/features`, esquemas `Problem` y `Page`, migración `V1` (users), JWT HS256 (ADR-006), manejador global de Problem Details y `Clock` en `America/La_Paz`.
- PostgreSQL 17, Flyway, Testcontainers (ADR-002). Frontend Vite + React TS con openapi-fetch y cliente generado versionado.
- C2 (`add-time-entries`) consumirá `Matter` y su `status`; C1 no conoce todavía la tabla `time_entries`.

## Goals / Non-Goals

**Goals:**

- Maestro de clientes y asuntos con autorización por rol en `application.service` (cubierta por JaCoCo).
- Generación de `MatterCode` libre de colisiones bajo concurrencia.
- Componentes de UI de listado, filtro y paginación reutilizables por C2 y C3.

**Non-Goals:**

- Editar o borrar clientes y asuntos (no hay `PUT`/`PATCH`/`DELETE`).
- Reabrir asuntos.
- Búsqueda de texto completo o por similitud (basta `ILIKE`).
- Auditoría de quién creó o cerró (se puede agregar después sin cambiar la spec).

## Decisions

### D1. Contrato OpenAPI

- Rutas: `GET/POST /api/clients`, `GET /api/clients/{clientId}`, `GET/POST /api/matters`, `GET /api/matters/{matterId}`, `POST /api/matters/{matterId}/close`. Todas con `security: bearerAuth`.
- Esquemas: `Client {id, name, taxId?, createdAt}`, `ClientCreate {name, taxId?}`, `Matter {id, code, clientId, clientName, title, description?, status, createdAt, closedAt?}`, `MatterCreate {clientId, title, description?}`, `MatterStatus enum [OPEN, CLOSED]`, `ClientPage` y `MatterPage` (composición de `Page` con `items` tipados).
- `MatterCreate` no declara `code`, `status` ni `closedAt`; con `additionalProperties` permitido, Jackson ignora campos desconocidos (`FAIL_ON_UNKNOWN_PROPERTIES=false`), lo que cumple el escenario "Campos de solo lectura ignorados". Alternativa descartada: rechazar con 400 campos desconocidos — más estricta, pero rompe clientes tolerantes y no la pide el brief.
- Ids `uuid` (v4 generados por la aplicación). Alternativa: `bigserial` — expone volumen y facilita enumeración.
- Respuestas de error declaradas por operación: 400, 401, 403, 404, 409 con `application/problem+json` → `Problem`.
- `close` responde `200` con el `Matter` (no `204`) para que la UI actualice la fila sin un `GET` extra.

### D2. Modelo de datos (Flyway `V2__clients_matters.sql`)

- `clients(id uuid pk, name varchar(200) not null, tax_id varchar(50) null, created_at timestamptz not null)` con índice único parcial `ux_clients_tax_id on clients(tax_id) where tax_id is not null`.
- `matters(id uuid pk, code varchar(12) not null unique, client_id uuid not null fk → clients, title varchar(200) not null, description varchar(2000) null, status varchar(6) not null check (status in ('OPEN','CLOSED')), created_at timestamptz not null, closed_at timestamptz null, version bigint not null default 0)` con `check ((status = 'CLOSED') = (closed_at is not null))`.
- Índices: `matters(client_id, status)`, `lower(clients.name)`, `lower(matters.title)` para los filtros `q`.
- `matter_code_seq(year int pk, last_value int not null)` para el correlativo anual.

### D3. Generación de `MatterCode`

- En la misma transacción del alta: `INSERT INTO matter_code_seq(year, last_value) VALUES (:y, 1) ON CONFLICT (year) DO UPDATE SET last_value = matter_code_seq.last_value + 1 RETURNING last_value`. El bloqueo de fila del upsert serializa las altas concurrentes del mismo año y garantiza códigos únicos (ver Riesgos sobre huecos).
- `:y` = `LocalDate.now(clock).getYear()` con el `Clock` en `America/La_Paz` (inyectado; ArchUnit prohíbe `now()` sin `Clock`).
- `MatterCode` es un value object de dominio que valida el patrón `^AS-\d{4}-\d{4}$` y formatea con ceros a la izquierda.
- Alternativas descartadas: `SELECT max(code)+1` (condición de carrera), secuencia Postgres única (no reinicia por año), `pg_advisory_xact_lock` (válido, pero el upsert es más simple y ya es atómico).

### D4. Unicidad de `taxId`

- Verificación previa en el servicio (`existsByTaxId`) para un mensaje claro, más traducción de `DataIntegrityViolationException` sobre `ux_clients_tax_id` a `ClientTaxIdTakenException` para la carrera entre dos altas. `taxId` se recorta (`trim`) antes de guardar y comparar; la comparación es exacta (sensible a mayúsculas).

### D5. Cierre de asunto

- `Matter.close(Instant now)` en el dominio: lanza `MatterAlreadyClosedException` si `status == CLOSED`; si no, fija `CLOSED` y `closedAt`.
- Concurrencia: bloqueo optimista con `version` (`@Version`). Si dos Socios cierran a la vez, el perdedor recibe `ObjectOptimisticLockingFailureException`; el servicio relee y, al ver `CLOSED`, responde `409 matter-already-closed` (no `concurrent-modification`), porque el resultado observable es el mismo que cerrar dos veces.
- El cierre no consulta registros de horas (Supuesto S1).

### D6. Autorización

- `application.service` recibe un `CurrentUser(userId, role)` desde el adaptador web y lanza `ForbiddenException` si una escritura no viene de `PARTNER`. No se usa `@PreAuthorize` en controllers para que la regla quede en `application` y bajo JaCoCo (ADR-001).
- Spring Security exige autenticación en `/api/**` salvo `/api/auth/login` y Actuator; el `AuthenticationEntryPoint` existente produce `401 unauthenticated`.
- Precedencia de errores `401 > 403 > 400 > 404 > 409` (Supuesto S11). La Bean Validation de las interfaces generadas (`@Valid`) se ejecuta antes de entrar al controller, así que el `403` por rol debe resolverse antes: `SecurityFilterChain` agrega reglas por URL para las escrituras (`POST /api/clients`, `POST /api/matters`, `POST /api/matters/*/close` → rol `PARTNER`) y el `AccessDeniedHandler` existente produce `403 forbidden`. El servicio conserva su verificación con `CurrentUser` (defensa en profundidad y cobertura JaCoCo). Dentro del servicio el orden es validar, luego buscar (`404`), luego aplicar la regla de estado (`409`). Alternativa descartada: validar el cuerpo a mano en el servicio después del chequeo de rol, porque duplica la validación que ya genera el contrato.
- Métodos no declarados (p. ej. `PATCH /api/matters/{id}`) los rechaza Spring MVC con `405`; el manejador global lo envuelve como Problem Details (`code` = `method-not-allowed`). Las reglas por URL de `SecurityFilterChain` solo cubren los métodos declarados (`POST`), así que un `PATCH`/`PUT`/`DELETE` autenticado llega a Spring MVC, que no encuentra ruta y responde `405` antes de cualquier chequeo de rol: un `LAWYER` recibe `405`, no `403`. La resolución del método precede a la precedencia `403 > 400` de S11 porque no existe operación que autorizar. Ver Supuesto S6 y D7.

### D7. ProblemCatalog

| Excepción de dominio                                                           | `code`                  | status | `title` (es)                            |
| ------------------------------------------------------------------------------ | ----------------------- | ------ | --------------------------------------- |
| `ClientNotFoundException`                                                      | `not-found`             | 404    | Recurso no encontrado                   |
| `MatterNotFoundException`                                                      | `not-found`             | 404    | Recurso no encontrado                   |
| `ClientTaxIdTakenException`                                                    | `client-tax-id-taken`   | 409    | Identificación tributaria ya registrada |
| `MatterAlreadyClosedException`                                                 | `matter-already-closed` | 409    | El asunto ya está cerrado               |
| `ForbiddenException` (existente)                                               | `forbidden`             | 403    | Acción no permitida para su rol         |
| `HttpRequestMethodNotSupportedException` (framework, sin excepción de dominio) | `method-not-allowed`    | 405    | Método no permitido                     |
| Cualquier otra excepción no catalogada (`Exception`, respaldo del manejador)   | `internal-error`        | 500    | Error interno                           |

`method-not-allowed` es un slug técnico del framework, no una regla de negocio. Lo produce el manejador global de Problem Details del skeleton para cualquier ruta de `/api`, así que C2–C4 lo heredan sin declararlo. La tarea 5.1 lo agrega a las convenciones de error compartidas.

**Errores sin detalles internos (compartido con C2–C4).** El manejador global tiene un `@ExceptionHandler(Exception.class)` de respaldo que responde `500 internal-error` con un `detail` fijo en español ("Ocurrió un error inesperado. Si persiste, informe el identificador de traza.") y el `traceId`; la excepción completa se registra en el log del servidor con ese `traceId`, nunca en la respuesta. Ninguna respuesta Problem Details copia `exception.getMessage()`, la clase de la excepción, la traza ni texto SQL: los `detail` salen del `ProblemCatalog` o de plantillas propias en español. Se configura además `server.error.include-stacktrace=never`, `server.error.include-message=never` y `server.error.include-exception=false` para los errores que no pasan por el manejador. Lo verifica un IT con un repositorio que lanza `DataAccessException` (tarea 2.7).

La validación de Bean Validation (`@Size`, `@NotBlank`, `@Min/@Max` en `page`/`size`, enum en `status`) produce `400 validation-error` con `errors[]` desde el manejador del skeleton. `name` y `title` se recortan antes de validar.

### D8. Consultas y paginación

- Spring Data con `Specification` para combinar filtros opcionales; `q` → `lower(col) like lower('%' || :q || '%')` con escape de `%` y `_`.
- Orden fijo: clientes por `name asc, id asc`; asuntos por `code desc`. `Pageable` se construye a partir de `page`/`size` ya validados; `totalPages` = `ceil(totalItems/size)`.
- `Matter.clientName` se resuelve con `join fetch` en la consulta para evitar N+1.

### D9. Frontend

- Rutas `/clientes` y `/asuntos` en el layout autenticado; entrada en el menú para ambos roles.
- Hook `usePagedQuery(path, filters)` sobre openapi-fetch; estado de filtros y página en la URL (`?q=&page=`), búsqueda con _debounce_ de 300 ms.
- Componentes reutilizables: `DataTable` (tabla accesible con `<caption>`), `Pagination`, `SearchInput`, `ConfirmDialog` (`<dialog>` nativo con foco atrapado).
- Visibilidad por rol con `useCurrentUser()` (`GET /api/me`): los botones **Nuevo cliente**, **Nuevo asunto** y **Cerrar asunto** se renderizan solo si `role === 'PARTNER'`. Es UX, no seguridad: la API es la que hace cumplir la regla.
- Los errores Problem Details se mapean por `code` a mensajes en español; `errors[]` se pinta junto a cada campo.
- El filtro de cliente en Asuntos usa un `<select>` con hasta 100 clientes (`GET /api/clients?size=100`); ver Supuesto S5.

### D10. Seed de demo

- `R__seed_clients_matters.sql` (perfil `demo`), idempotente (`ON CONFLICT DO NOTHING`), con 25 clientes sintéticos y ~30 asuntos mixtos `OPEN`/`CLOSED` del año en curso, para ejercitar la paginación en E2E.

## Risks / Trade-offs

- [Un rollback después del upsert del correlativo deja un hueco en `NNNN`] → Aceptado: la spec exige unicidad y orden, no ausencia de huecos; el upsert y el insert van en la misma transacción, por lo que solo un fallo del insert del asunto (raro tras validar) genera hueco.
- [Más de 9999 asuntos en un año desbordan `NNNN`] → Fuera del alcance del POC; el dominio lanza error si el correlativo supera 9999 (Supuesto S4).
- [La UI oculta botones pero un Abogado podría llamar la API] → La autorización se aplica en `application.service` y hay tests 403 por cada escritura.
- [`ILIKE '%q%'` no usa índice b-tree] → Volumen del POC es pequeño; si crece, `pg_trgm`.
- [El `<select>` de clientes con 100 elementos no escala] → Suficiente para el POC; reemplazable por un combo con búsqueda sin cambiar la spec.

## Migration Plan

- `V2__clients_matters.sql` es aditiva; no toca `users`. Rollback: revertir el PR antes del merge (el POC no tiene datos productivos); en entornos ya migrados, `docker compose down -v` recrea la base.
- El cliente generado se regenera y versiona en el mismo PR (`npm run api:generate`, verificado por `npm run api:check`).

## Trazabilidad

| Regla del brief                                                        | Requisito (`specs/clients-matters/spec.md`)                      | Escenario(s)                                                                                                                                                                                                                    |
| ---------------------------------------------------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Alta de cliente: `name` 1–200, `taxId` opcional                        | El Socio crea clientes; Validación de datos del cliente          | Alta de cliente con identificación tributaria; Alta de cliente sin identificación tributaria; Nombre ausente; Nombre de más de 200 caracteres; Nombre de exactamente 200 caracteres; taxId de más de 50 caracteres; taxId vacío |
| `taxId` único → `409 client-tax-id-taken`                              | Identificación tributaria única                                  | taxId duplicado; Varios clientes sin taxId                                                                                                                                                                                      |
| Alta de asunto: `title` 1–200, `description` ≤ 2000, cliente existente | El Socio crea asuntos; Validación de datos del asunto            | Alta de asunto; Cliente del asunto inexistente; Título ausente y clientId ausente; Descripción de más de 2000 caracteres; Título de más de 200 caracteres                                                                       |
| Código `AS-YYYY-NNNN` generado por el servidor                         | Código de asunto generado por el servidor; El Socio crea asuntos | Primer asunto del año; Correlativo siguiente; Cambio de año según la zona de la firma; Altas concurrentes; Campos de solo lectura ignorados                                                                                     |
| Estado inicial `OPEN`                                                  | El Socio crea asuntos                                            | Alta de asunto; Campos de solo lectura ignorados                                                                                                                                                                                |
| Filtro `q` de clientes                                                 | Listado paginado de clientes con filtro por nombre               | Abogado lista clientes filtrando por nombre; Listado sin filtro                                                                                                                                                                 |
| Filtros `q` / `clientId` / `status` de asuntos                         | Listado paginado de asuntos con filtros                          | Filtrar por cliente y estado; Buscar por código; Buscar por título; Estado inválido                                                                                                                                             |
| Paginación `page`/`size` y envoltorio                                  | Paginación de listados                                           | Segunda página de asuntos; Tamaño de página mayor al máximo; Página negativa; size no numérico; Página fuera de rango                                                                                                           |
| Consulta por id y `404 not-found`                                      | Consulta de cliente por id; Consulta de asunto por id            | Cliente existente; Cliente inexistente; Asunto existente; Asunto inexistente                                                                                                                                                    |
| Cierre → `CLOSED` + `closedAt`                                         | El Socio cierra asuntos                                          | Cierre exitoso; Cierre de asunto inexistente                                                                                                                                                                                    |
| Cierre permitido con horas pendientes                                  | El Socio cierra asuntos                                          | El cierre no impone condiciones sobre registros de horas (el IT con `PENDING` llega en C2, ver S1)                                                                                                                              |
| `409 matter-already-closed`                                            | Un asunto cerrado no se vuelve a cerrar                          | Cerrar dos veces                                                                                                                                                                                                                |
| Sin reapertura                                                         | Un asunto cerrado no se reabre                                   | Intento de reabrir con PATCH; Abogado envía un método no ofrecido; La interfaz no ofrece reabrir                                                                                                                                |
| `400 validation-error` en Problem Details                              | Errores en formato Problem Details                               | Estructura de un error de validación                                                                                                                                                                                            |
| Errores claros sin fugas de detalles internos (RFC 9457)               | Errores sin detalles internos                                    | Error inesperado de persistencia                                                                                                                                                                                                |
| `401 unauthenticated`                                                  | Autenticación obligatoria en clientes y asuntos                  | Listado de clientes sin token; Creación de asunto con token expirado                                                                                                                                                            |
| `403 forbidden` en escrituras de `LAWYER`, y precedencia de errores    | El Abogado solo puede leer                                       | Abogado intenta crear un cliente; Abogado intenta crear un asunto; Abogado intenta cerrar un asunto; Abogado cierra un asunto inexistente; Abogado envía un cuerpo inválido; Cuerpo inválido con cliente inexistente            |
| UI en español: pantalla Clientes con búsqueda y paginación             | Pantalla Clientes                                                | Buscar un cliente; Navegar a la página siguiente; Búsqueda sin resultados                                                                                                                                                       |
| UI: pantalla Asuntos con filtros                                       | Pantalla Asuntos                                                 | Filtrar asuntos abiertos de un cliente; Buscar por código                                                                                                                                                                       |
| UI: formularios de alta solo para el Socio                             | Formularios de alta solo para el Socio                           | Socio crea un cliente desde la pantalla; Socio crea un asunto y ve su código; Error de validación en el formulario; Identificación tributaria duplicada en el formulario; Abogado no ve formularios de alta                     |
| UI: cierre con confirmación                                            | Cierre de asunto con confirmación en la interfaz                 | Socio confirma el cierre; Socio cancela el cierre; Asunto cerrado por otro Socio mientras tanto; Abogado no ve la acción de cierre                                                                                              |

## Supuestos

- **S1.** Cerrar un asunto está permitido aunque tenga registros de horas `PENDING`; esos registros no cambian de estado al cerrar. C2 decide (Supuesto 13 de `add-time-entries`): un registro pendiente de un asunto cerrado puede aprobarse o rechazarse, pero no editarse (`422 matter-closed`). En C1 no existe `time_entries`, así que el escenario de la spec solo verifica que el cierre no impone condiciones sobre horas. C2 (`add-time-entries`) agrega el IT "cerrar un asunto con registros `PENDING` → `200 CLOSED`".
- **S2.** `taxId` es texto libre de 1 a 50 caracteres (no se valida formato de NIT boliviano), se recorta y su unicidad es exacta, sensible a mayúsculas.
- **S3.** Un `clientId` inexistente en `POST /api/matters` responde `404 not-found` (se aplica la regla general "ids desconocidos → 404"), no `422`.
- **S4.** El año del código es el de la fecha de creación en `America/La_Paz`; el correlativo empieza en `0001` cada año y está limitado a 9999 por año.
- **S5.** El listado de clientes ordena por `name asc`; el de asuntos por `code desc` (más recientes primero). El filtro `q` es coincidencia parcial sin distinguir mayúsculas; no ignora tildes.
- **S6.** No hay endpoints `PUT`/`PATCH`/`DELETE` para clientes ni asuntos; un método no ofrecido responde `405` con Problem Details `code` = `method-not-allowed` (slug técnico del framework, registrado en D7 y en las convenciones compartidas), cualquiera sea el rol: un `LAWYER` que envía `PATCH`/`PUT`/`DELETE` a `/api/matters/{id}` recibe `405`, no `403`, porque la resolución del método ocurre antes de la autorización (no existe ruta que autorizar; ver D6).
- **S7.** Campos desconocidos o de solo lectura en el cuerpo de un alta (`code`, `status`, `closedAt`, `id`) se ignoran en silencio.
- **S8.** Cualquier usuario autenticado (ambos roles) puede leer todos los clientes y asuntos; no hay restricción por asignación de abogado a asunto en C1.
- **S9.** `name` y `title` se recortan (espacios al inicio y al final) antes de validar longitud y guardar.
- **S10.** Un `size` o `page` no numérico responde `400 validation-error` con `errors[].field` igual al parámetro.
- **S11.** Cuando una petición incumple varias reglas, se responde con la primera según `401 unauthenticated` > `403 forbidden` > `400 validation-error` > `404 not-found` > `409` (conflictos de estado). Un `LAWYER` recibe `403` aunque el cuerpo sea inválido o el recurso no exista. La precedencia aplica a operaciones declaradas: un método no ofrecido responde `405 method-not-allowed` antes de evaluar el rol (S6). Un Socio con cuerpo inválido recibe `400` aunque el `clientId` no exista. C2–C4 deben aplicar la misma precedencia base; `403 forbidden` (rol) se evalúa siempre antes que `400`. Excepciones acordadas entre cambios: en C2, `403 not-entry-owner` exige cargar el registro, por lo que se evalúa después de `404 not-found` y antes de `409`, y los `422` de reglas de negocio van después de `409` (design D5 de `add-time-entries`); en C4, `404 feature-disabled` se evalúa antes de `403` para que el endpoint sea indistinguible de inexistente con el indicador apagado (design D2 de `add-description-assistant`).

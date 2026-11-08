## Context

Ver `proposal.md` (Why). C2 se construye sobre el walking skeleton de F3 (login JWT, `GET /api/me`, Problem Details, `Clock` configurable, ArchUnit) y sobre C1 (`add-clients-matters`: `Matter` con `MatterStatus` `OPEN`/`CLOSED`). Restricciones fijadas por ADR-001 (hexagonal + API-first con interfaces generadas), ADR-002 (pirámide de pruebas, `Clock.fixed`, Testcontainers con `postgres:17-alpine`, JaCoCo ≥ 80 % en `domain` + `application`) y ADR-006 (JWT HS256, rol en el token). Los requisitos observables están en `specs/time-entries/spec.md`; aquí solo se decide el cómo.

## Goals / Non-Goals

**Goals:**

- Reglas de negocio puras en `domain` (sin Spring), testeables con `Clock.fixed` en `America/La_Paz`.
- Un slug de Problem Details por regla, registrado en `ProblemCatalog` y verificado por ArchUnit.
- Límite diario correcto bajo concurrencia, demostrado por un IT con Testcontainers.
- Autorización por rol y por propiedad en `application.service` (cubierta por JaCoCo).

**Non-Goals:**

- Borrado de registros, reapertura de registros rechazados o aprobación masiva.
- Notificaciones (correo, push) al aprobar o rechazar.
- `GET /api/time-entries/{id}` individual: no lo pide el brief; la UI trabaja con el listado.
- Tablero y agregaciones (C3) y asistente de descripción (C4).

## Decisions

### D1. Horas como décimas enteras

`hours_tenths SMALLINT NOT NULL CHECK (hours_tenths BETWEEN 1 AND 240)`. La API expone `hours` como `number` (p. ej. `1.5`); el adaptador web solo deserializa a `BigDecimal` (sin redondeo) y pasa el valor crudo al dominio. El value object de dominio `Hours.of(BigDecimal)` hace la conversión y las dos reglas: `hours.multiply(10)` debe tener escala 0 tras `stripTrailingZeros`, si no → `HoursInvalidIncrementException` (`hours-invalid-increment`); luego `1 ≤ décimas ≤ 240`, si no → `HoursOutOfRangeException` (`hours-out-of-range`). Se evalúa primero el incremento y después el rango. Así ambas reglas viven en `domain`, se prueban con unit tests puros y cuentan para el JaCoCo de `domain` + `application`. _Alternativa descartada:_ `NUMERIC(4,1)`; complica la suma exacta en Java y no aporta nada frente a enteros.

### D2. "Hoy" y la ventana de edición con `Clock`

`LocalDate.now(clock)` con `clock` en zona `America/La_Paz` (bean del skeleton, configurable). Se guarda `created_at TIMESTAMPTZ`; la ventana se evalúa como `today(La_Paz) <= createdAt.atZone(La_Paz).toLocalDate().plusDays(7)`. ArchUnit ya prohíbe `*.now()` sin `Clock`. _Alternativa descartada:_ 7 × 24 h desde el instante de creación; menos legible para el usuario ("7 días calendario").

### D3. Serialización del límite diario con advisory lock

En la transacción de alta o edición se toma `pg_advisory_xact_lock(hashtext('time-entry:' || lawyer_id || ':' || work_date))` antes de sumar `PENDING` + `APPROVED`. En una edición que cambia `workDate` se bloquean ambas fechas en orden ascendente para evitar interbloqueos. Como altas y ediciones toman la misma clave, también quedan serializadas un alta y una edición concurrentes del mismo abogado y día (requisito "Altas y ediciones concurrentes respetan el límite diario"). El puerto de salida `DailyHoursLock` aísla esta primitiva de PostgreSQL del dominio. _Alternativas descartadas:_ aislamiento `SERIALIZABLE` (reintentos y errores 40001 a mapear), restricción por trigger (lógica fuera del dominio y difícil de testear).

### D4. Concurrencia optimista con `@Version`

La columna `version BIGINT` se mapea con `@Version` de JPA. El servicio compara la `version` del request con la cargada antes de modificar (→ `ConcurrentModificationException` de dominio → 409 `concurrent-modification`) y además captura `OptimisticLockingFailureException` por si la carrera ocurre entre la lectura y el commit. Approve/reject no reciben `version`: el chequeo de estado bajo `SELECT … FOR UPDATE` basta para que el segundo reciba 409 `entry-not-pending`.

### D5. Orden de evaluación de errores

Para que los tests sean deterministas cuando un request viola varias reglas: 401 → 403 `forbidden` (rol) → 400 `validation-error` (forma) → 404 `not-found` (registro y luego asunto) → 403 `not-entry-owner` → 409 `entry-not-pending` → 409 `concurrent-modification` → 422 `edit-window-expired` → 422 `matter-closed` → 422 `future-date` → 422 `hours-invalid-increment` → 422 `hours-out-of-range` → 422 `daily-hours-exceeded`. En el rechazo, 409 `entry-not-pending` se evalúa antes que 422 `rejection-reason-required`.

### D6. Problem Details y extensión del límite diario

Nuevas entradas en `ProblemCatalog`: `hours-invalid-increment`, `hours-out-of-range`, `daily-hours-exceeded`, `future-date`, `matter-closed`, `edit-window-expired`, `entry-not-pending`, `concurrent-modification`, `not-entry-owner`, `rejection-reason-required` (los genéricos `validation-error`, `unauthenticated`, `forbidden`, `not-found` vienen del skeleton). `daily-hours-exceeded` agrega `limitHours` y `alreadyLoggedHours` (number, horas decimales) en el contrato como esquema `DailyHoursExceededProblem` (`allOf` de `Problem`).

### D7. Contrato

Esquemas: `TimeEntry` (`id`, `matterId`, `matterCode`, `lawyerId`, `lawyerName`, `workDate`, `hours`, `description`, `status`, `rejectionReason?`, `createdAt`, `editableUntil`, `version`), `TimeEntryCreate`, `TimeEntryUpdate` (+ `version` requerido), `RejectRequest` (`reason`), `TimeEntryPage`. `editableUntil` (fecha en La Paz) permite que la UI decida si muestra "Editar" sin duplicar la regla del reloj; el servidor sigue siendo la autoridad. Altas: 201 con cabecera `Location`. `rejection-reason-required` se valida en el servicio (no con Bean Validation) para devolver 422 y no 400. Ordenación del listado (requisito de listado): parámetro `sort` como enum cerrado en el contrato con dos valores, `workDate,desc` (por defecto; desempate `createdAt` desc) y `createdAt,asc` (lo usa la cola de aprobaciones junto con `status=PENDING`); cualquier otro valor → 400 `validation-error`. `editableUntil` = fecha de creación en La Paz + 7 (Supuesto 2), se calcula en el dominio y se expone siempre, aunque el registro ya no sea `PENDING`.

### D8. Frontend

Rutas `/registrar-horas` y `/mis-registros` (rol `LAWYER`), `/aprobaciones` (rol `PARTNER`), protegidas por un guard de rol con el `role` de `GET /api/me`. Parseo de horas: `"1,5"` y `"1.5"` → `1.5` antes de enviar; validación previa en cliente de incremento y rango (el servidor sigue validando). Un mapa `code → mensaje en español` centralizado; si el `code` no está en el mapa se muestra el `detail` del servidor (ya en español). Diálogo de rechazo accesible (foco atrapado, `aria-modal`), contador de caracteres 0/500.

### D9. Persistencia y seed

Migración `V3__time_entries.sql` con FK a `users` y `matters`, índice `(lawyer_id, work_date)` y `CHECK (status IN ('PENDING','APPROVED','REJECTED'))` y `CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)`. Seed `R__demo_time_entries.sql` idempotente, con fechas relativas (`CURRENT_DATE - N`, N ≥ 1), solo con el perfil `demo`.

## Supuestos

1. **El socio no registra ni edita horas** (403 `forbidden`). El brief no lo dice explícitamente; se asume por la separación de roles del glosario.
2. **"7 días calendario desde la creación"** significa: editable mientras la fecha de hoy en `America/La_Paz` sea ≤ fecha de creación en La Paz + 7 (creado el día 0, editable hasta el día 7 incluido).
3. **El abogado que envía `lawyerId` en el listado** no recibe error: el filtro se ignora y solo ve lo suyo (evita revelar la existencia de otros abogados).
4. **`matterId` inexistente** en alta o edición → 404 `not-found` (no 422), coherente con la política de estados.
5. **`description` y `reason` se recortan** (trim) antes de validar longitud; descripción fuera de 1..2000 → 400 `validation-error` (formato), mientras que motivo vacío o > 500 → 422 `rejection-reason-required` (lo pide el brief explícitamente).
6. **La edición puede cambiar el asunto**; la regla `matter-closed` se evalúa sobre el asunto destino. Si el asunto original se cerró después de la carga y no se cambia, la edición también recibe 422 `matter-closed`.
7. **El socio puede aprobar o rechazar registros de cualquier abogado**, sin restricción por asunto ni autoaprobación (el socio no carga horas).
8. **`alreadyLoggedHours`** son las horas `PENDING` + `APPROVED` del abogado ese día sin el registro nuevo (o sin las horas previas del registro editado).
9. **Approve/reject no exigen `version`**; la serialización por fila resuelve la carrera entre dos socios (el segundo recibe 409 `entry-not-pending`).
10. **Solo se listan registros**; no hay endpoint de detalle ni de borrado en C2.
11. **El orden del listado solo admite los dos valores de `sort` definidos en D7**; no hay ordenación libre por cualquier campo.
12. Los asuntos de la lista desplegable de "Registrar horas" vienen del listado de C1 filtrado por `status=OPEN`.
13. **Cerrar un asunto no altera sus registros** (Supuesto S1 de `add-clients-matters`): los registros `PENDING` de un asunto `CLOSED` siguen `PENDING`, el socio puede aprobarlos o rechazarlos (approve/reject no evalúan `matter-closed`) y el autor no puede editarlos (422 `matter-closed`, Supuesto 6).

## Risks / Trade-offs

- [El advisory lock depende de PostgreSQL] → Aislado tras el puerto `DailyHoursLock`; el IT de concurrencia con Testcontainers usa la misma imagen que compose.
- [Interbloqueo al mover un registro de fecha] → Bloqueo de las dos claves en orden ascendente (D3).
- [Bordes de zona horaria (UTC vs La Paz)] → Unit tests con `Clock.fixed` a 2026-11-08T02:30Z y E2E con fecha de hoy calculada en La Paz.
- [La UI calcula "editable" con un reloj distinto] → La UI usa `editableUntil` del servidor; el servidor responde 422 si de todos modos vence.
- [Mensajes en español desincronizados de los slugs] → Test unitario del frontend que exige un mensaje para cada slug de C2 del contrato.

## Migration Plan

Migración Flyway `V3` hacia adelante, sin datos previos. Rollback: revertir el PR (base de demo efímera; `compose down -v`).

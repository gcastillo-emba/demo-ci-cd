## Context

Ver `proposal.md` (Why, Propósito de la capacidad y la tabla de trazabilidad brief → requisito → escenario) para la motivación y `specs/hours-dashboard/spec.md` para los requisitos. Restricciones que dan forma al diseño:

- Arquitectura hexagonal y API-first (ADR-001): el contrato `api/openapi.yaml` es la fuente única; el controller implementa la interfaz generada y la autorización por rol vive en `application.service`.
- Los registros de horas (C2, `add-time-entries`) guardan `hours_tenths` (décimas enteras, 1..240), `work_date`, `status` (`PENDING|APPROVED|REJECTED`) y el autor (`lawyer_id`); los asuntos (C1, `add-clients-matters`) aportan `code` y `title`; los usuarios (skeleton) aportan el nombre.
- "Hoy" se obtiene de un `Clock` inyectado en `America/La_Paz` (ArchUnit prohíbe `*.now()` sin `Clock`).
- Errores RFC 9457 con el `ProblemCatalog` compartido; JWT HS256 (ADR-006).
- Pruebas según ADR-002: unit con `Clock.fixed`, aplicación con fakes en memoria, IT con Testcontainers `postgres:17-alpine`, Vitest + Testing Library, Playwright.

## Goals / Non-Goals

**Goals:**

- Una sola consulta agregada de solo lectura, eficiente y con el filtro de alcance aplicado en el servidor.
- Aritmética exacta: sumar décimas enteras y convertir a decimal solo al serializar.
- Tablas accesibles y barras CSS sin librerías de gráficos.

**Non-Goals:**

- Paginación del resumen (el volumen de un POC con datos sintéticos lo hace innecesario; ver Supuestos).
- Filtros por cliente, asunto o abogado; exportación; gráficos interactivos; selector de rango en la UI.
- Caché del resumen o vistas materializadas.
- Nuevas migraciones de esquema (solo se agregan índices si el plan de consulta lo justifica y un seed `R__` de demo).

## Decisions

### D1. Contrato: un endpoint de lectura con respuesta plana

`GET /api/dashboard/summary?from=YYYY-MM-DD&to=YYYY-MM-DD` (ambos `format: date`, opcionales) devuelve `DashboardSummary { from, to, byMatter: MatterHoursRow[], byLawyer: LawyerHoursRow[], totals: HoursTotals }`. Horas como `number` con `multipleOf: 0.1`, `minimum: 0`. Respuestas `200`, `400` y `401` con `Problem` (`application/problem+json`).

- Alternativa: dos endpoints (`/by-matter`, `/by-lawyer`). Descartada: dos viajes, riesgo de totales inconsistentes entre llamadas y más superficie de contrato.
- Alternativa: envolver en `Page`. Descartada: es un agregado acotado, no una colección navegable.

### D2. Validación de la ventana en el caso de uso, no solo en el contrato

El controller recibe `from`/`to` como `LocalDate` (el formato inválido lo rechaza el binding y se mapea a `validation-error` con `field`). Las reglas cruzadas (ambos o ninguno, `from <= to`, `días(from..to inclusive) <= 92`) se implementan en un value object de dominio `DateWindow` con fábricas `lastDays(clock, zone, 30)` y `of(from, to)`, que lanza una `DomainValidationException` con la lista de `{field, message}`; el `ProblemCatalog` la mapea a `400 validation-error`.

- Alternativa: Bean Validation en el DTO generado. Descartada: no expresa bien reglas entre campos y dejaría la regla fuera de la cobertura JaCoCo de `domain`.

### D3. Alcance por rol en `application.service`

`DashboardQueryService` (puerto `in` `GetDashboardSummary`) recibe el usuario autenticado (id + rol) y construye un `DashboardScope`: `ALL` para `PARTNER`, `OWN(lawyerId)` para `LAWYER`. El puerto `out` `DashboardReadRepository.aggregate(DateWindow, DashboardScope)` recibe el alcance ya resuelto; el adaptador nunca decide permisos. Con alcance `OWN`, si la consulta por abogado no devuelve fila (el abogado no tiene horas contadas en la ventana), el servicio agrega la fila propia con `approvedHours` = 0 y `pendingHours` = 0, con el `lawyerName` del usuario autenticado (Supuesto 1); así un `LAWYER` recibe siempre exactamente una fila en `byLawyer`. Los parámetros de consulta desconocidos (p. ej. `lawyerId`) no existen en el contrato y se ignoran.

- Alternativa: filtrar en el controller o en la consulta según el token. Descartada: la regla quedaría fuera de JaCoCo y duplicada.

### D4. Agregación en SQL con sumas condicionales en décimas

El adaptador de persistencia ejecuta dos consultas nativas (o JPQL con proyecciones) sobre `time_entries` unida a `matters` / `users`, con `WHERE work_date BETWEEN :from AND :to AND status IN ('APPROVED','PENDING')` y, si el alcance es `OWN`, `AND lawyer_id = :lawyerId`:

- `SUM(CASE WHEN status='APPROVED' THEN hours_tenths ELSE 0 END)` y su equivalente `PENDING`, `GROUP BY` asunto (orden `code ASC`) y por abogado (orden `name ASC`).
- Los totales se calculan en `application` sumando las filas por asunto (garantiza la consistencia que exige la spec) y se comprueba en test que coinciden con la suma por abogado.
- La conversión a decimal (`tenths / 10` como `BigDecimal` con escala 1) ocurre en el mapper web.
- Índice sugerido `time_entries(work_date, status)` y `(lawyer_id, work_date)` si no existen ya en C2; se decide con `EXPLAIN` en el IT.

- Alternativa: cargar los registros y agregar en Java. Descartada: transfiere datos innecesarios y escala peor.

### D5. Frontend: pantalla "Tablero" con componentes propios

- Ruta `/tablero`, enlace "Tablero" en la navegación para ambos roles. Llama a `GET /api/dashboard/summary` **sin** `from`/`to` (la ventana fija la decide el servidor y se muestra con los `from`/`to` de la respuesta en formato `dd/mm/aaaa`).
- Componente `HoursTable` reutilizado para ambas tablas: `<table>` con `<caption>`, `<th scope="col">` (Asunto/Abogado, Aprobadas, Pendientes, Distribución) y `<th scope="row">` por fila.
- Componente `HoursBar`: dos `<span>` con `width` en porcentaje relativo al máximo `approved+pending` de la tabla (si ese máximo es 0, como en la fila propia en cero de un abogado sin horas, ambos segmentos tienen ancho 0 y queda solo el texto); segmento aprobado sólido y pendiente con patrón rayado (`repeating-linear-gradient`) además de colores distintos con contraste AA; texto visible "Aprobadas: X h · Pendientes: Y h". La barra lleva `aria-hidden="true"` porque los valores ya están en celdas de texto.
- Formato de horas con `Intl.NumberFormat('es-BO', { maximumFractionDigits: 1 })` → "3,5 h", "4 h", "0 h".
- Estados: carga (texto "Cargando tablero…" con `aria-busy`), vacío (mensaje de la spec), error (`role="alert"`), `401` → manejador global del skeleton que redirige al login.

- Alternativa: librería de gráficos (Recharts, Chart.js). Descartada: dependencia extra, peor accesibilidad por defecto y el plan exige barras CSS.

### D6. Seed de demo

`R__seed_dashboard_demo.sql` (perfil `demo`) no crea datos propios si el seed de C2 ya cubre la ventana; en caso contrario agrega registros sintéticos con fechas relativas a `CURRENT_DATE` (N ≥ 1 días atrás) en los tres estados, para que el tablero muestre aprobadas, pendientes y un rechazado excluido.

## Risks / Trade-offs

- [El `CURRENT_DATE` de PostgreSQL usa la zona del servidor, no `America/La_Paz`] → la ventana siempre se calcula en Java con el `Clock` y se pasa como parámetros; el seed solo usa fechas relativas aproximadas (N ≥ 1 días) que no dependen de la hora exacta.
- [Totales inconsistentes por redondeo] → se suman décimas enteras y se convierte una sola vez al serializar.
- [Fuga de datos de otros abogados al `LAWYER`] → filtro en el caso de uso + IT de alcance por rol que verifica ausencia de nombres y asuntos ajenos (test exigido por el plan).
- [Dependencia de C1 y C2] → el PR `feat(c3)` no se abre hasta que ambos estén fusionados; el contrato de C3 referencia esquemas existentes.
- [Barras poco legibles con valores muy pequeños] → el texto es la fuente de verdad; la barra es complementaria (`aria-hidden`).
- [Ventana de 92 días con mucho volumen] → índices de D4; fuera del alcance optimizar más en el POC.

## Migration Plan

Sin cambios de tablas. La tarea 2.7 encontró un escaneo completo de `time_entries` en la consulta del socio, por lo que se agregó la migración aditiva `V4__dashboard_indexes.sql` con el índice `ix_time_entries_work_date_status (work_date, status)` (Supuesto 19); las consultas del abogado usan el índice existente `ix_time_entries_lawyer_work_date`. Despliegue con la imagen normal de CD; rollback = revertir el PR (el endpoint es de solo lectura y no altera datos).

## Supuestos

1. **Abogado sin horas en la ventana (decidido):** el `byLawyer` de un `LAWYER` tiene siempre exactamente una fila, la suya, tal como dice el brief ("exactamente una fila: él mismo"); si no tiene horas contadas en la ventana, la fila va con `approvedHours` = 0.0 y `pendingHours` = 0.0, mientras `byMatter` queda vacío y los totales en cero. La regla "período sin horas → listas vacías" aplica a `byMatter` y, para el `PARTNER`, también a `byLawyer`. Es reversible (cambia una fila en la respuesta, no el contrato) y se registra en `docs/glosario.md` (tarea 5.1). Recogido en la spec, requisitos "Alcance del abogado", "Período sin horas" y "Estado vacío del Tablero".
2. **Solo aparecen filas con horas contadas:** asuntos y abogados sin registros `APPROVED`/`PENDING` en la ventana no aparecen (no se rellenan con ceros), incluidos asuntos `OPEN` sin horas. Única excepción: la fila propia del `LAWYER` (Supuesto 1).
3. **Asuntos cerrados:** un asunto `CLOSED` con horas en la ventana sí aparece; el estado del asunto no filtra.
4. **Fecha de referencia:** la ventana filtra por `workDate` (fecha de trabajo), no por la fecha de creación ni de aprobación.
5. **`from` y `to` van juntos:** enviar solo uno es `400 validation-error`; no se completa el otro con un valor por defecto.
6. **Fechas futuras:** se permiten en `from`/`to` siempre que el rango cumpla las reglas (devolverán normalmente vacío, porque C2 no admite fechas futuras).
7. **Cálculo del límite de 92 días:** se cuentan ambos extremos (`to - from + 1 <= 92`); 2026-08-08..2026-11-07 es válido y 2026-08-07..2026-11-07 no.
8. **Campo en `errors[]`:** el exceso de rango se reporta en `to`; `from > to` en `from`; parámetro faltante en el que falta; formato inválido en el parámetro afectado.
9. **Orden:** `byMatter` por `matterCode` ascendente; `byLawyer` por `lawyerName` ascendente (colación por defecto de la base).
10. **Formato numérico:** la API devuelve decimales con punto y una décima; la UI usa coma decimal y omite ",0" en enteros ("4 h").
11. **Sin paginación ni 403:** ambos roles pueden consultar el tablero (no hay caso `403 forbidden`); el resumen no se pagina.
12. **Parámetros desconocidos se ignoran** (no producen `400`).
13. **Nombre del abogado:** `lawyerName` es el nombre para mostrar del usuario sintético definido en el skeleton.

Supuestos agregados durante la construcción (dev-backend, dev-frontend, integración):

14. **Formato de fecha estricto (backend):** el controller usa un editor de fechas propio: solo se acepta `\d{4}-\d{2}-\d{2}` seguido de un parseo estricto, por lo que `2026-02-30` y `+2026-11-01` se rechazan. Si ambos parámetros están mal formados, ambos aparecen en `errors[]`. El mensaje es fijo ("Debe ser una fecha válida con formato AAAA-MM-DD.") y nunca repite la entrada.
15. **Parámetros vacíos:** `from=` (vacío) cuenta como no enviado, igual que el comportamiento por defecto de Spring. Vacíos en ambos → ventana por defecto; vacío en uno solo → error de parámetro faltante (Supuesto 5).
16. **Mensajes de la ventana:** parámetro faltante → "Es obligatorio cuando se informa la otra fecha del rango."; `from > to` → "La fecha inicial no puede ser posterior a la final."; exceso → "El rango no puede superar 92 días.". En una llamada directa al dominio con ambas fechas ausentes se reportan los dos campos, pero el servicio usa la ventana por defecto en ese caso.
17. **Excepción de validación con varios campos:** se agregó `DomainValidationException` en `domain.shared` con una lista de errores por campo; `ProblemCatalog` la mapea a `400 "Solicitud inválida"` y el manejador la expone como `validation-error` con `errors[]`. `InvalidValueException` (un solo campo) no cambia.
18. **Totales consistentes:** las consultas por asunto y por abogado corren en una única transacción de solo lectura `REPEATABLE READ`, de modo que `byLawyer` suma lo mismo que `totals` aunque haya escrituras concurrentes.
19. **Índice (tarea 2.7):** sin índice, la consulta del socio hacía un escaneo completo de `time_entries`; `V4__dashboard_indexes.sql` agrega `ix_time_entries_work_date_status (work_date, status)`. `DashboardReadAdapterIT` ejecuta `EXPLAIN` con los escaneos secuenciales desactivados y comprueba que ninguna de las cuatro consultas recorre la tabla completa (ver "Migration Plan").
20. **Regla de seguridad explícita:** `GET /api/dashboard/summary` tiene una regla de URL que admite `PARTNER` y `LAWYER`. Un token válido siempre lleva uno de esos roles, así que sigue sin existir un `403` alcanzable (Supuesto 11).
21. **Abogado ausente en `users`:** si el `LAWYER` autenticado no tiene horas y su usuario no existe en `users` (no debería ocurrir con un token válido), el servicio lanza `UserNotFoundException`, que el catálogo mapea a `404`; está cubierto por un test de aplicación.
22. **Seed de demo (tarea 2.8, design D6):** no se agregó un `R__` nuevo: el seed de C2 `R__seed_time_entries.sql` ya tiene registros `APPROVED`, `PENDING` y `REJECTED` con fechas relativas de 1 a 9 días atrás, dentro de la ventana por defecto. La verificación con compose la cubren el E2E del Tablero y la evidencia.
23. **Formato de horas en la UI:** se sigue design D5 (`Intl.NumberFormat('es-BO', {maximumFractionDigits: 1})`: "3,5 h", "4 h", "0 h") con un helper propio del tablero, no el `formatHours` de C2.
24. **Estado vacío en E2E y evidencia:** el seed de demo siempre tiene horas dentro de los 30 días, así que el estado vacío del E2E y de la captura usa un `page.route` que simula la respuesta de `/api/dashboard/summary`. El comportamiento real con ventana vacía lo cubren los IT del backend (tarea 4.3).
25. **Sesión expirada en E2E:** el E2E abre `/tablero` sin sesión, en lugar de con un token expirado, para no sumar inicios de sesión bajo el límite de nginx (5 por minuto por IP); la redirección al login es la misma. El token expirado lo cubren el IT del backend y el test Vitest.
26. **Sin códigos de problema nuevos en la UI:** el frontend nunca envía `from`/`to`, por lo que un `400` muestra el mensaje genérico de `validation-error`.
27. **Rótulo de asunto y leyenda:** la cabecera de fila por asunto es "código — título", como en C2. Una leyenda breve bajo las tablas indica que el segmento sólido es aprobadas, el rayado es pendientes y que las rechazadas no se cuentan.

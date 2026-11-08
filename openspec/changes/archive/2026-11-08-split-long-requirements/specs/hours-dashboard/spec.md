## MODIFIED Requirements

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

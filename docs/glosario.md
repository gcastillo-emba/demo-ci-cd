# Glosario — Asuntos & Horas

Lenguaje ubicuo del POC. Regla: **documentación y UI en español; código, identificadores, contrato OpenAPI y base de datos en inglés.** Si un término no está aquí, se agrega antes de usarlo en una spec o en el código.

## Dominio

| Español (docs/UI)   | Identificador en inglés     | Definición                                                                                                                   |
| ------------------- | --------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Cliente             | `Client`                    | Persona u organización (sintética) a la que la firma presta servicios.                                                       |
| Asunto              | `Matter`                    | Caso o encargo de un cliente al que se imputan horas.                                                                        |
| Código de asunto    | `MatterCode` (`matterCode`) | Identificador legible y único del asunto (value object).                                                                     |
| Estado del asunto   | `MatterStatus`              | `OPEN` o `CLOSED`.                                                                                                           |
| Abierto             | `OPEN`                      | Asunto que admite nuevos registros de horas.                                                                                 |
| Cerrado             | `CLOSED`                    | Asunto que no admite nuevos registros.                                                                                       |
| Registro de horas   | `TimeEntry`                 | Horas trabajadas por un abogado en un asunto en una fecha, con descripción.                                                  |
| Fecha de trabajo    | `workDate`                  | Día en que se realizó el trabajo (no la fecha de carga). "Hoy" se calcula con `Clock` en `America/La_Paz`.                   |
| Horas               | `hours` / `hoursTenths`     | Duración en **décimas de hora enteras** (`hours_tenths`, 1..240; 15 = 1,5 h). La API puede exponer `hours` decimal derivado. |
| Descripción         | `description`               | Texto libre del trabajo realizado.                                                                                           |
| Estado del registro | `TimeEntryStatus`           | `PENDING`, `APPROVED` o `REJECTED`.                                                                                          |
| Pendiente           | `PENDING`                   | Registro cargado, editable dentro de la ventana de 7 días, a la espera de revisión.                                          |
| Aprobada            | `APPROVED`                  | Registro aceptado por un socio. Estado terminal.                                                                             |
| Rechazada           | `REJECTED`                  | Registro rechazado por un socio con motivo. Estado terminal.                                                                 |
| Motivo de rechazo   | `rejectionReason`           | Texto obligatorio al rechazar.                                                                                               |
| Límite diario       | `dailyLimit`                | Máximo de 24 h por abogado y día, sumando PENDING y APPROVED.                                                                |
| Ventana de edición  | `editWindow`                | 7 días desde la creación del registro durante los cuales el autor puede editarlo.                                            |
| Tablero             | `Dashboard`                 | Vista de horas agregadas (ventana fija de 30 días), con alcance según el rol.                                                |

## Personas y seguridad

| Español          | Identificador             | Definición                                                                   |
| ---------------- | ------------------------- | ---------------------------------------------------------------------------- |
| Usuario          | `User`                    | Cuenta de acceso sintética (BCrypt).                                         |
| Rol              | `Role`                    | `PARTNER` o `LAWYER`.                                                        |
| Abogado          | `Lawyer` (rol `LAWYER`)   | Registra y edita sus propias horas; ve solo lo suyo.                         |
| Socio            | `Partner` (rol `PARTNER`) | Gestiona clientes y asuntos, aprueba/rechaza horas y ve el tablero de todos. |
| Inicio de sesión | `login`                   | `POST /api/auth/login`; devuelve un JWT (ADR-006).                           |
| Usuario actual   | `me`                      | `GET /api/me`.                                                               |
| Token de acceso  | `accessToken`             | JWT HS256, TTL 2 h, en `sessionStorage`.                                     |
| Propiedad        | `ownership`               | Regla de autorización: un abogado solo opera sobre sus registros.            |

## Asistente de descripción (C4)

| Español                    | Identificador                                       | Definición                                                                                  |
| -------------------------- | --------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Asistente de descripción   | `DescriptionAssistant`                              | Puerto que propone una descripción mejorada a partir del borrador.                          |
| Sugerencia                 | `suggestion`                                        | Texto propuesto (máx. 300 caracteres, nunca vacío); el usuario la acepta, edita o descarta. |
| Borrador                   | `draft`                                             | Texto de entrada del usuario (1–500 caracteres).                                            |
| Adaptador simulado         | `fake`                                              | Implementación determinista por defecto, usada en tests y evals.                            |
| Adaptador Anthropic        | `anthropic`                                         | Implementación real vía HTTP; inactiva sin `ANTHROPIC_API_KEY`.                             |
| Indicador de funcionalidad | feature flag (`app.features.description-assistant`) | Interruptor de configuración expuesto en `GET /api/features`.                               |
| Guardrail                  | `guardrail`                                         | Límite verificable sobre entrada/salida del asistente.                                      |
| Eval                       | `eval case`                                         | Caso YAML en `evals/description-assistant/cases/` que verifica comportamiento.              |

## API y errores

| Español               | Identificador               | Definición                                                                                                                      |
| --------------------- | --------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Contrato              | `api/openapi.yaml`          | Fuente única de la API (OpenAPI 3.1, ADR-001).                                                                                  |
| Detalle de problema   | Problem Details (`Problem`) | Respuesta de error RFC 9457: `type`, `title`, `status`, `detail` (en español), `code` estable en inglés, `errors[]`, `traceId`. |
| Código de error       | `code` / slug               | Identificador estable del error, p. ej. `daily-limit-exceeded`.                                                                 |
| Catálogo de problemas | `ProblemCatalog`            | Mapa `DomainException` → slug, status y título.                                                                                 |
| Página                | `Page`                      | Respuesta paginada (`items`, `page`, `size`, `totalItems`).                                                                     |
| Error de validación   | `400`                       | Formato o campos inválidos.                                                                                                     |
| Conflicto             | `409`                       | Estado incompatible o concurrencia.                                                                                             |
| Regla de negocio      | `422`                       | Violación de una regla del dominio.                                                                                             |

## Metodología (MADA-MB)

| Español             | Identificador / artefacto              | Definición                                                                                         |
| ------------------- | -------------------------------------- | -------------------------------------------------------------------------------------------------- |
| Capacidad           | `c1`…`c4`                              | Unidad funcional entregada en un PR (C1 clientes/asuntos, C2 registros, C3 tablero, C4 asistente). |
| Cambio OpenSpec     | `openspec/changes/<id>`                | Propuesta, specs, diseño y tareas de una capacidad.                                                |
| Requisito normativo | `SHALL (DEBE)` / `SHALL NOT (NO DEBE)` | Convención para pasar `openspec validate --strict` (bitácora #3).                                  |
| Gate mecánico       | —                                      | Verificación determinista (CI, ArchUnit, hooks).                                                   |
| Gate de juicio      | —                                      | Verificación con criterio (revisor, seguridad, humano).                                            |
| Supuesto explícito  | —                                      | Hecho no verificado declarado en ADR o `design.md`.                                                |
| Bitácora de fallas  | `docs/bitacora-fallas.md`              | Registro obligatorio de gates fallidos, errores y herramientas ausentes.                           |
| ADR                 | `docs/adr/ADR-NNN-*.md`                | Registro de decisión de arquitectura.                                                              |
| Arnés               | `npm run gate` / `/gate`               | Ejecución local de todos los gates mecánicos (ADR-002).                                            |

## Why

Los abogados cargan descripciones de horas breves e informales ("llamada cliente contrato") que luego el Socio debe corregir antes de facturar. Un asistente que proponga una descripción profesional en español, con el humano siempre decidiendo (aceptar, editar o descartar), reduce ese retrabajo y permite demostrar en el POC una capacidad agéntica gobernada: detrás de un indicador de funcionalidad, con guardrails verificables, sin PII en logs y con evals deterministas en CI.

## What Changes

- Nuevo endpoint `POST /api/assistant/description-suggestions` con cuerpo `{ note, matterId? }` que devuelve `{ suggestion, provider }`. Solo rol `LAWYER`; cualquier otro rol recibe `403 forbidden`.
- Nuevo indicador de funcionalidad `description-assistant`, expuesto en `GET /api/features`: apagado en el perfil de producción y encendido en el perfil `demo` (supuesto). Con el indicador apagado el endpoint responde `404 feature-disabled` y la UI oculta el botón.
- Guardrails de entrada: la nota se recorta (trim); vacía → `400 validation-error` con error de campo `assistant-note-empty`; más de 500 caracteres → `400 validation-error` con `assistant-note-too-long`.
- Guardrails de salida: la sugerencia nunca es vacía y tiene como máximo 300 caracteres (se trunca en límite de palabra si el proveedor devuelve más).
- Disponibilidad: si el proveedor no responde dentro del tiempo límite (8 s), el endpoint responde `503 assistant-unavailable` con mensaje en español.
- Privacidad: el texto de la nota y de la sugerencia nunca se escribe en logs; solo longitudes, proveedor y latencia.
- Humano en el circuito: el endpoint **nunca** persiste la sugerencia. En el formulario "Registrar horas" se agrega el botón "Sugerir descripción" con las acciones Aceptar, Editar y Descartar; el registro solo se guarda cuando el abogado envía el formulario.
- Evals: exactamente 10 casos en `evals/description-assistant/cases/*.yaml`; corren con el adaptador `fake` en CI (bloqueante) y opcionalmente contra el modelo real con `workflow_dispatch` manual.

## Capabilities

### New Capabilities

- `description-assistant`: sugerencia de descripción profesional para un registro de horas, con indicador de funcionalidad, autorización por rol, guardrails de entrada/salida, manejo de indisponibilidad del proveedor, privacidad en logs, flujo humano en el circuito en la UI y criterios de aceptación de evals.

### Modified Capabilities

<!-- Ninguna: GET /api/features y el formulario "Registrar horas" se extienden sin cambiar requisitos existentes; las specs de C1–C3 aún no están archivadas en openspec/specs/. -->

## Impact

- **Contrato** (`api/openapi.yaml`): nueva operación `POST /api/assistant/description-suggestions`, esquemas `DescriptionSuggestionRequest` y `DescriptionSuggestionResponse`; respuestas `400`, `401`, `403`, `404`, `503` con `application/problem+json`; nuevo campo `description-assistant` en la respuesta de `GET /api/features`.
- **Backend**: puerto de salida `DescriptionAssistant`, adaptadores `fake` y `anthropic`, caso de uso con guardrails, nuevos slugs en `ProblemCatalog` (`feature-disabled`, `assistant-unavailable`) y códigos de campo `assistant-note-empty` / `assistant-note-too-long`; configuración `app.features.description-assistant` y `app.assistant.*`.
- **Frontend**: botón y panel de sugerencia en el formulario "Registrar horas" (C2), condicionado por `GET /api/features`; cliente generado regenerado.
- **Evals y CI**: `evals/description-assistant/cases/` (10 casos), job `evals` bloqueante en `ci.yml`, workflow manual `evals-real.yml` (inerte sin `ANTHROPIC_API_KEY`).
- **Dependencias**: ninguna nueva; el adaptador real usa `java.net.http` del JDK.
- **Depende de**: walking skeleton (F3: auth, `/api/features`, Problem Details) y `add-time-entries` (C2: formulario "Registrar horas").

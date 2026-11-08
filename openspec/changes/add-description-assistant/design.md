## Context

Ver `proposal.md` (Why). El walking skeleton (F3) ya aporta autenticación JWT (ADR-006), `GET /api/features`, Problem Details RFC 9457 con `ProblemCatalog`, `Clock` y la arquitectura hexagonal verificada por ArchUnit (ADR-001). C2 (`add-time-entries`) aporta el formulario "Registrar horas". No hay `ANTHROPIC_API_KEY` en el entorno (bitácora #2), por lo que todo gate debe ser determinista sin red. Los requisitos observables están en `specs/description-assistant/spec.md`; este documento solo fija el cómo.

## Goals / Non-Goals

**Goals:**

- Un único puerto de salida `DescriptionAssistant` con dos adaptadores intercambiables por configuración, sin que el resto del sistema sepa cuál está activo (salvo el campo `provider` de la respuesta).
- Guardrails de entrada y salida implementados en `application` (cubiertos por JaCoCo), no en los adaptadores.
- Evals ejecutables en CI sin red ni secretos.

**Non-Goals:**

- Streaming de respuestas, historial de sugerencias o reintentos automáticos.
- Usar el contenido del asunto (nombre del cliente, etc.) en el prompt: `matterId` solo se valida y se pasa como contexto mínimo (código del asunto), nunca datos del cliente.
- Medición de calidad del modelo real como gate (es informativo, `evals-real.yml`).
- Cuotas o rate limiting por usuario.

## Decisions

### D1. Puerto y caso de uso

- `application.port.out.DescriptionAssistant` con `String suggest(AssistantPrompt prompt)` y `String providerName()`. `AssistantPrompt` es un record de dominio (`note`, `Optional<MatterCode>`).
- `application.port.in.SuggestDescriptionUseCase` implementado por `application.service.DescriptionSuggestionService`, que en orden: verifica el indicador (`FeatureFlags` en `config` inyectado vía puerto) → verifica rol `LAWYER` → recorta y valida la nota → resuelve `matterId` si viene → invoca el puerto con timeout → normaliza la salida → registra metadatos.
- La autorización por rol y la verificación del indicador viven en el servicio (no en Spring Security) para quedar cubiertas por JaCoCo, igual que en C1–C3.
- **Alternativa descartada:** validar en el controller con Bean Validation. Se descartó porque el recorte previo y los códigos de campo propios (`assistant-note-empty`, `assistant-note-too-long`) quedan más claros y testeables en el servicio.

### D2. Orden de verificaciones y estados HTTP

Indicador apagado (`404 feature-disabled`) se evalúa **antes** del rol, para que el endpoint sea indistinguible de inexistente con el flag apagado. Sin token → `401` lo resuelve el filtro de seguridad antes de todo. Luego rol (`403 forbidden`), validación (`400`), asunto (`404 not-found`), proveedor (`503`).

### D3. Adaptador `fake` (default)

- Paquete `adapters.out.assistant.fake`. Determinista y puro: normaliza la nota (minúsculas, sin acentos), detecta verbos/palabras clave de un diccionario fijo (reunión, llamada, redacción, revisión, demanda, contrato, audiencia, escrito…) y compone una plantilla en español ("Revisión del contrato de arrendamiento y reunión con el cliente para …").
- Elimina de la salida cualquier token con forma de correo, teléfono (≥ 7 dígitos) o monto; no introduce nombres propios ni montos que no estén en la nota.
- Permite forzar comportamientos solo en tests mediante un doble de prueba separado (no en el adaptador productivo) para simular texto largo, vacío, error y demora.

### D4. Adaptador `anthropic`

- Paquete `adapters.out.assistant.anthropic`. Usa `java.net.http.HttpClient` (sin SDK, sin dependencia nueva) contra la Messages API.
- Configuración `app.assistant.anthropic.{model, max-tokens, base-url}`; `max-tokens` con tope duro de 300 en el binding de configuración (`@Max`). La clave se lee de la variable de entorno `ANTHROPIC_API_KEY`; si falta, el adaptador lanza `AssistantUnavailableException` en cada llamada (→ `503`) y el arranque registra un aviso sin el valor.
- Prompt de sistema fijo en español: redactar una descripción profesional de máximo 300 caracteres, sin inventar nombres ni montos, sin repetir correos ni teléfonos.
- Probado con un servidor HTTP simulado (sin red real), según ADR-002.

### D5. Selección de adaptador

`app.assistant.provider` = `fake` (default) | `anthropic`, con `@ConditionalOnProperty` en `config`. Valor desconocido → fallo de arranque. El campo `provider` de la respuesta es `providerName()` del bean activo.

### D6. Timeout

El servicio envuelve la llamada en `CompletableFuture.supplyAsync(..).orTimeout(app.assistant.timeout)` con default `8s`, usando un executor acotado; además el `HttpClient` del adaptador real fija `connectTimeout` y `request.timeout` al mismo valor. Timeout, excepción del adaptador o salida vacía → `AssistantUnavailableException` → `503 assistant-unavailable`.

### D7. Normalización de salida

`SuggestionPolicy` (dominio, puro): recorta, colapsa espacios, y si supera 300 caracteres corta en el último espacio dentro de los primeros 300 (si no hay espacio, corta exactamente en 300, cortando la palabra; cubierto por el escenario "Proveedor devuelve un texto largo sin espacios"). Si el resultado queda vacío → no disponible.

### D8. Errores y contrato

- Nuevos slugs en `ProblemCatalog`: `feature-disabled` (404) y `assistant-unavailable` (503). `validation-error`, `forbidden`, `not-found`, `unauthenticated` ya existen.
- Los códigos `assistant-note-empty` y `assistant-note-too-long` viajan en `errors[].code` (ver Supuestos).
- Contrato: operación `suggestDescription` (tag `assistant`), `DescriptionSuggestionRequest { note: string (maxLength no se fija en el esquema para que el guardrail responda con el código propio), matterId?: uuid }`, `DescriptionSuggestionResponse { suggestion: string (minLength 1, maxLength 300), provider: enum [fake, anthropic] }`; `Features` agrega `description-assistant: boolean`.

### D9. Logs sin PII

Un único evento ECS por solicitud: `assistant.note_length`, `assistant.suggestion_length`, `assistant.provider`, `assistant.latency_ms`, `assistant.outcome` (`ok|validation|unavailable|disabled|forbidden`). Nunca se loguean el cuerpo de la solicitud ni la respuesta; las excepciones del adaptador se envuelven sin mensaje del proveedor (que podría contener el texto). Se verifica con un test de captura de logs (appender en memoria) que busca la nota, el correo, el teléfono y la sugerencia en todas las líneas.

### D10. Frontend

- `useFeatures()` (ya existente en el skeleton) decide si renderizar `<DescriptionAssistantButton>` dentro del formulario "Registrar horas".
- Estado local `idle | loading | suggestion | error`; el panel de sugerencia es una región `aria-live="polite"`. Aceptar copia y cierra; Editar copia y hace `focus()` + selección al final; Descartar limpia el estado. Ninguna acción envía el formulario.
- Errores: se mapea `code` → mensaje en español (`assistant-unavailable`, `validation-error` + `assistant-note-empty`/`too-long`, genérico).

### D11. Evals

- `evals/description-assistant/cases/NN-<slug>.yaml` con `id`, `note`, `matterCode?`, `expect: { status, fieldCode?, nonEmpty, maxLength: 300, language: es, mustContainAny: [...], mustNotContain: [...], noNewNumbers, noNewProperNames }`.
- Runner: test JUnit en perfil Maven `-Pevals` (`DescriptionAssistantEvalsIT`) que levanta el contexto web con `fake` y llama al endpoint real vía MockMvc; aserta exactamente 10 archivos. `npm run evals` lo invoca. Detección de español: heurística de stopwords (≥ 3 de una lista fija) para no agregar dependencias.
- En CI el runner es el job `evals`, requerido por el check agregador `ci-ok` (gate obligatorio del PR en la spec).
- `evals-real.yml` (`workflow_dispatch`) corre el mismo perfil con `app.assistant.provider=anthropic`; guard `if: secrets.ANTHROPIC_API_KEY != ''` y sube un reporte como artefacto. No bloquea nada.

## Risks / Trade-offs

- [El fake es demasiado simple y los evals pasan sin reflejar calidad real] → Se acepta: los evals con fake protegen guardrails y contrato; la calidad del modelo real se mide con `evals-real.yml` manual (ADR-002, supuesto 3).
- [Heurística de español o de "nombres inventados" con falsos positivos con el modelo real] → Criterios conservadores (palabras con mayúscula inicial no presentes en la nota fuera de inicio de oración); en el workflow real el resultado es informativo.
- [Fuga de PII vía mensaje de excepción o logs de `HttpClient`] → Wrapping sin mensaje original, nivel de log de `jdk.httpclient` en WARN, test de captura de logs.
- [Hilos colgados por timeout] → Executor acotado y `HttpClient` con su propio timeout; el `CompletableFuture` cancelado no retiene la solicitud HTTP del servlet.
- [Prompt injection en la nota hacia el modelo real] → La salida igual pasa por `SuggestionPolicy` (longitud, no vacía), nunca se persiste sin acción humana y no se ejecuta; riesgo residual aceptado para un POC con datos sintéticos.
- [Acoplamiento con el formulario de C2] → C4 se implementa después de C2; el componente es aditivo y se oculta con el flag apagado.

## Migration Plan

Sin migraciones de base de datos. Despliegue: el flag está apagado en producción por defecto, por lo que la imagen puede publicarse sin efecto visible. Rollback: poner `app.features.description-assistant=false` (el endpoint pasa a `404` y la UI oculta el botón) sin redeploy de código.

## Supuestos

1. El indicador está **apagado por defecto en el perfil de producción y encendido en el perfil `demo`**; se configura con `app.features.description-assistant`.
2. El nombre del indicador en `GET /api/features` es exactamente `description-assistant` (booleano).
3. El esquema compartido de error de validación (`errors[]`) se extiende de forma compatible con un campo opcional `code` para transportar `assistant-note-empty` / `assistant-note-too-long`; `field` y `message` no cambian. Los demás cambios (`add-clients-matters`, `add-time-entries`, `add-hours-dashboard`) definen `errors[]` como `{field, message}`; el campo es aditivo y opcional, por lo que no los contradice, pero el cambio en el esquema `Problem` compartido de `api/openapi.yaml` lo coordina el dueño del contrato y se mergea antes de C4 (tarea 1.2) para que una regeneración posterior del contrato no lo elimine.
4. Con el indicador apagado se responde `404 feature-disabled` incluso a usuarios con rol `PARTNER` (el flag se evalúa antes del rol, D2); cubierto por el escenario "Socio con el indicador apagado".
5. `matterId`, si se envía, debe ser un UUID de un asunto existente (cualquier estado); formato inválido → `400 validation-error` con `errors[].field = matterId` (escenario "matterId con formato inválido"), inexistente → `404 not-found`. Solo se usa su código como contexto, nunca datos del cliente.
6. El límite de 500 caracteres de la nota se mide en caracteres Unicode (code points) después del recorte; el límite de 300 de la sugerencia, igual.
7. Una salida vacía del proveedor se trata como indisponibilidad (`503 assistant-unavailable`), no como `200` con texto de relleno.
8. Timeout por defecto de 8 s, configurable con `app.assistant.timeout`; el escenario de spec admite 1 s de margen.
9. En la UI, la nota que se envía al asistente es el contenido actual del campo descripción del formulario "Registrar horas".
10. Modelo por defecto del adaptador real configurable (`app.assistant.anthropic.model`); el valor concreto se fija al implementar según la referencia vigente de la API y no afecta la spec.
11. `evals-real.yml` es manual e informativo; sin `ANTHROPIC_API_KEY` termina en verde sin ejecutar casos, con un aviso.

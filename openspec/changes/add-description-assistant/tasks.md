## 1. Contrato

- [ ] 1.1 Agregar a `api/openapi.yaml` la operación `POST /api/assistant/description-suggestions` (tag `assistant`, seguridad Bearer) con `DescriptionSuggestionRequest { note, matterId? }` y `DescriptionSuggestionResponse { suggestion (1..300), provider [fake, anthropic] }`, y respuestas `200`, `400`, `401`, `403`, `404`, `503` en `application/problem+json`; verificar con `npm run api:lint` en verde.
- [ ] 1.2 Agregar `description-assistant: boolean` al esquema de `GET /api/features` y el campo opcional `code` en los elementos de `errors[]` del esquema `Problem` compartido (supuesto 3); el esquema `Problem` lo define el contrato compartido (introducido por `add-clients-matters`), así que este cambio se coordina con el dueño del contrato y se mergea en `main` antes del PR de C4, de modo que una regeneración posterior no elimine `errors[].code`; verificar con `npm run api:lint`, revisión de diff sin cambios incompatibles y aprobación del dueño del contrato en el PR.
- [ ] 1.3 Regenerar el cliente con `npm run api:generate` y commitear `frontend/src/api/generated/schema.ts`; verificar con `npm run api:check` en verde.

## 2. Backend

- [ ] 2.1 Crear `SuggestionPolicy` y `AssistantPrompt` en `domain` (recorte, truncado en límite de palabra a 300, vacío → no disponible); verificar con tests unitarios de 0, 1, 300, 301 y 450 caracteres y texto sin espacios.
- [ ] 2.2 Crear el puerto `DescriptionAssistant` y el caso de uso `SuggestDescriptionUseCase` / `DescriptionSuggestionService` con el orden flag → rol → validación → asunto → proveedor (design D2); verificar con tests de aplicación con fakes que cubran cada escenario de la spec (flag apagado, `PARTNER`, nota vacía, solo espacios, 500 con espacios, 501, asunto inexistente).
- [ ] 2.3 Implementar el timeout configurable (`app.assistant.timeout`, default 8 s) y el mapeo de error/vacío a `AssistantUnavailableException`; verificar con tests que usan un doble lento, uno que lanza y uno que devuelve vacío, sin `Thread.sleep` en las aserciones.
- [ ] 2.4 Registrar `feature-disabled` (404) y `assistant-unavailable` (503) en `ProblemCatalog` y los códigos de campo `assistant-note-empty` / `assistant-note-too-long`; verificar con la regla ArchUnit de catálogo y un test por slug.
- [ ] 2.5 Implementar el adaptador `fake` determinista (design D3); verificar con tests de determinismo, ausencia de correo/teléfono/montos y salida en español.
- [ ] 2.6 Implementar el adaptador `anthropic` con `java.net.http` (modelo y `max-tokens` configurables con tope 300, clave desde `ANTHROPIC_API_KEY`); verificar con test unitario contra servidor HTTP simulado (éxito, 5xx, timeout, clave ausente) y la regla ArchUnit de aislamiento del adaptador.
- [ ] 2.7 Configurar `app.assistant.provider` (default `fake`) y `app.features.description-assistant` (apagado en producción, encendido en `demo`); verificar con IT que `GET /api/features` devuelve `false`/`true` según el perfil.
- [ ] 2.8 Implementar el controller que implementa la interfaz generada; verificar con IT web (Testcontainers) de `200`, `401`, `403`, `400` (ambos códigos), `404 feature-disabled`, `404 not-found`, `503` y que el número de registros de horas no cambia tras una sugerencia.
- [ ] 2.9 Emitir el evento de log de metadatos sin PII (design D9); verificar con test de captura de logs que no aparecen la nota, el correo, el teléfono ni la sugerencia, en éxito y en `503`.
- [ ] 2.10 Ejecutar `mvnw verify`; verificar JaCoCo ≥ 80 % en `domain` + `application`, ArchUnit y Spotless en verde.

## 3. Frontend

- [ ] 3.1 Mostrar el botón "Sugerir descripción" en el formulario "Registrar horas" solo si `description-assistant` es `true`; verificar con tests Vitest para ambos valores del indicador.
- [ ] 3.2 Implementar el panel de sugerencia con Aceptar, Editar (copia y foco) y Descartar, sin enviar el formulario; verificar con tests Vitest + Testing Library de cada acción y de que no se llama a la creación de registros.
- [ ] 3.3 Mostrar errores en español (`assistant-unavailable`, nota vacía, nota demasiado larga, genérico) conservando el texto del campo; verificar con tests Vitest con respuestas simuladas.
- [ ] 3.4 Ejecutar lint, `prettier --check`, typecheck y Vitest del frontend; verificar todos en verde.

## 4. QA y evidencia

- [ ] 4.1 Escribir exactamente 10 casos en `evals/description-assistant/cases/*.yaml` que cubran no vacía, ≤ 300, español, palabras clave requeridas, sin nombres ni montos inventados, sin eco de correo/teléfono, rechazo de nota vacía y de 501 caracteres; verificar con `npm run evals` (fake): 10 de 10 en verde y fallo si el conteo es distinto de 10.
- [ ] 4.2 Agregar el job `evals` bloqueante a `ci.yml` (dependencia de `ci-ok`) y `evals-real.yml` con `workflow_dispatch` y guard de secreto; verificar con `ci-ok` verde en el PR y ejecución del workflow manual que termina sin casos y con aviso al no haber secreto.
- [ ] 4.3 Escribir E2E Playwright: flujo Sugerir → Aceptar → Registrar con perfil `demo`, y botón ausente con el indicador apagado; verificar con `npm run gate -- --e2e` en verde.
- [ ] 4.4 Guardar capturas del flujo (sugerencia mostrada, aceptada, editada, descartada, error 503) en `docs/evidence/add-description-assistant/`; verificar que están enlazadas en el PR.
- [ ] 4.5 Mapear cada escenario de la spec a al menos un test (por nombre o comentario); verificar con una tabla de trazabilidad en el PR revisada por `revisor`.

## 5. Cierre

- [ ] 5.1 Actualizar `docs/glosario.md` si surgieron términos nuevos y registrar fallas del ciclo en `docs/bitacora-fallas.md`; verificar con revisión del diff.
- [ ] 5.2 Obtener las etiquetas `revisor:aprobado` y `seguridad:ok` posteriores al último commit (seguridad revisa privacidad en logs y manejo de `ANTHROPIC_API_KEY`); verificar en el PR.
- [ ] 5.3 Ejecutar `openspec validate add-description-assistant --strict --no-interactive` y luego `openspec archive add-description-assistant` como último commit del PR `feat(c4)`; verificar que `openspec/specs/description-assistant/spec.md` existe y `npm run spec:validate` queda en verde antes del merge con `scripts/merge-si-verde.mjs`.

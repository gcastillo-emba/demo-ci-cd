## Context

OpenSpec 1.14.1 agregó una advertencia para requisitos de más de 500 caracteres y `--strict` convierte las advertencias en errores. Con 1.15.0, `npm run spec:validate` da 0 de 4 specs válidas por 17 requisitos largos (entre 501 y 1097 caracteres). La advertencia sugiere mover ejemplos y casos borde a los escenarios, o partir el requisito en varios que enuncien un comportamiento cada uno.

OpenSpec 1.15.0 también rechaza un `MODIFIED` que no conserve todos los escenarios del requisito actual («MODIFIED … omits scenario(s) the current spec still has»), tanto en `validate` como en `archive`, y rechaza un `ADDED` con el mismo nombre que un `REMOVED`. Por eso un escenario solo puede pasar a otro requisito si el requisito de origen se elimina (`REMOVED`) y sus partes se agregan con nombres nuevos (`ADDED`).

## Goals / Non-Goals

**Goals:**

- Que las cuatro specs pasen `openspec validate --all --strict` con la versión nueva del validador.
- Conservar exactamente el comportamiento especificado y la trazabilidad escenario → test.
- Conservar el nombre de los requisitos que el código cita en comentarios.

**Non-Goals:**

- Reescribir o mejorar la redacción de requisitos que ya cumplen el límite.
- Cambiar código, contrato, tests o evidencia.

## Decisions

### D1. Acortar en su lugar siempre que se pueda; partir solo lo que no entra

Partir un requisito obliga a renombrarlo (ver Context), lo que deja desactualizadas las referencias por nombre y mueve el requisito al final de la spec. Por eso se parte solo cuando el texto no puede bajar de 500 caracteres sin perder contenido normativo: "Criterios de aceptación de evals" (744) y "Listado de registros con filtros y paginación" (1097). Ninguno de los dos se cita en el código. Los otros 15 se acortan con `MODIFIED`, con su nombre y todos sus escenarios.

### D2. Cómo se acorta sin cambiar el significado

Por orden de preferencia:

1. **Mover al escenario** un ejemplo o un caso borde que el escenario ya verifica:
   - "Un asunto cerrado no se reabre": la explicación de por qué la precedencia `403 > 400` no aplica pasa al escenario "Abogado envía un método no ofrecido", junto al "(no `403`)" que ya tenía.
   - "Formularios de alta solo para el Socio": la respuesta `409 client-tax-id-taken` pasa al escenario "Identificación tributaria duplicada en el formulario", cuyo resultado ya exigía el mensaje junto al campo.
   - "Cierre de asunto con confirmación en la interfaz": la respuesta `409 matter-already-closed` pasa al escenario "Asunto cerrado por otro Socio mientras tanto", cuyo resultado ya exigía el mensaje y el estado `Cerrado`.
   - "Pantalla Tablero": el ejemplo de formato del período pasa al escenario "El socio abre el Tablero".
   - "Sugerencia de descripción para el Abogado": el detalle de `errors[]` con `field: matterId` queda solo en el escenario "matterId con formato inválido", que ya lo exigía.
2. **Quitar lo que repite otro texto normativo**:
   - "El Socio crea asuntos": los límites de `title` (1 a 200 tras recortar) y `description` (hasta 2000), que ya define "Validación de datos del asunto".
   - "El Abogado solo puede leer": la frase "Así, a un `LAWYER` se le responde `403` aunque el cuerpo sea inválido o el recurso no exista", que ya verifican los escenarios "Abogado cierra un asunto inexistente" y "Abogado envía un cuerpo inválido".
   - "La nota se trata solo como datos": el ejemplo de instrucción inyectada, que ya está en el escenario "Nota con instrucciones inyectadas".
   - "Campos controlados por el servidor": la frase inicial, que resume la siguiente (la que enumera los campos y exige ignorarlos en silencio).
   - "Errores sin detalles internos": la glosa en inglés "(_stack traces_)".
   - "Alcance del abogado": la referencia "(ver `design.md`, Supuesto 1)" al diseño del cambio archivado.
3. **Condensar la redacción** con el mismo significado: "Validación de la nota de entrada" ("el mismo error con código `assistant-note-too-long`"), "El Socio cierra asuntos", "El Abogado solo puede leer", "Alcance del abogado", "Rechazo de registros con motivo obligatorio" y "Pantalla "Aprobaciones"".

### D3. Partición en requisitos de un solo comportamiento

En los dos requisitos partidos, cada frase normativa va a un requisito propio con el texto original y, cuando hace falta, el endpoint explícito para que se lea solo ("En `GET /api/time-entries`, …"). Los escenarios se mueven sin tocar nombre ni texto.

### D4. Versión del validador

Se sube a 1.15.0: es la última versión publicada hace al menos dos semanas (2026-10-24; 1.16.0 es del 2026-10-30 y 1.16.1 del 2026-11-07). Desde ahí Dependabot vuelve a proponer actualizaciones de OpenSpec dentro del grupo `dev-tooling`.

## Risks / Trade-offs

- [Una redacción condensada podría leerse distinto] → Mitigación: el PR incluye el diff palabra por palabra de cada requisito; ningún escenario pierde ni gana aserciones salvo el detalle movido desde su requisito (D2.1).
- [Los requisitos partidos quedan al final de la spec y con nombre nuevo] → Mitigación: solo dos, ninguno citado en el código; el orden de una spec no tiene significado.
- [Versiones futuras del validador pueden agregar reglas nuevas] → Mitigación: el gate `spec` del PR de Dependabot lo detecta antes del merge (lección de la fila 44 de la bitácora).

## Migration Plan

Sin migración: el cambio es solo de specs y herramientas. Rollback: revertir el PR.

## Supuestos

1. **Mover un caso borde al escenario no cambia su carácter normativo:** en OpenSpec los escenarios son criterios de aceptación del requisito, así que un comportamiento descrito en el escenario sigue siendo obligatorio.
2. **Contexto agregado al partir:** cuando una frase separada dependía del requisito de origen para saber a qué endpoint aplica, se le agrega ("En `GET /api/time-entries`", "Cada elemento de `items` de `GET /api/time-entries`"). No amplía ni restringe el comportamiento.
3. **"Los 10 casos de eval":** al separar la cobertura de los casos, "En conjunto los casos" pasa a "En conjunto, los 10 casos de eval", porque el requisito ya no sigue a la frase que los presenta.
4. **Evidencia de C2 sin tocar:** `docs/evidence/add-time-entries/trazabilidad.md` sigue citando "Listado de registros con filtros y paginación"; es evidencia del cambio archivado y no se reescribe.

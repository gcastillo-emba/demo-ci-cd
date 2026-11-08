---
name: adr
description: Crea un Architecture Decision Record (ADR) numerado en docs/adr/ con la plantilla del proyecto y lo enlaza en el índice. Úsala cuando haya que registrar una decisión técnica, una desviación del stack o de la metodología MADA-MB, o un supuesto relevante e irreversible.
---

# ADR (registro de decisión de arquitectura)

## Cuándo crear un ADR

- Desviación del stack o de la arquitectura decidida (plan maestro §3).
- Desviación consciente de la metodología, como el merge por agente (ADR-004).
- Decisión con trade-offs que otra persona cuestionaría en seis meses.
- Un supuesto que no cabe en el `design.md` de un único cambio porque afecta a varios.

Para una decisión local a un cambio OpenSpec basta con `design.md § Decisiones/Supuestos`.

## Procedimiento

1. **Número siguiente**: listar `docs/adr/ADR-*.md` con Glob y tomar el máximo NNN + 1, con tres dígitos.
2. **Nombre de archivo**: `docs/adr/ADR-NNN-titulo-en-kebab-case.md`, con el título en español y sin tildes en el nombre.
3. **Contenido**: copiar `docs/adr/plantilla.md` y completar todas las secciones:
   - **Estado**: Propuesto, Aceptado, Reemplazado por ADR-XXX u Obsoleto.
   - **Fecha**: absoluta (AAAA-MM-DD).
   - **Contexto**: el problema y sus restricciones; enlazar las filas de bitácora o los PRs que lo motivan.
   - **Decisión**: en voz activa ("Usamos…").
   - **Alternativas consideradas**: al menos dos, con el motivo del descarte.
   - **Consecuencias**: positivas, negativas y **qué gates las verifican**.
   - **Supuestos explícitos**.
   - **Cómo revertir**.
4. **Índice**: agregar una fila en `docs/adr/README.md` (número, título enlazado, estado, fecha).
5. **Reemplazo**: si el ADR sustituye a otro, cambiar el estado del anterior a "Reemplazado por ADR-NNN" y enlazar ambos.
6. **Formato**: correr `npx prettier --write docs/adr`. El hook de formateo lo hace automáticamente al editar.
7. **Revisión**: el ADR viaja en el PR que implementa la decisión, o en un PR `docs(adr): …` propio, y lo revisa el subagente `revisor`.

## Reglas

- Un ADR no se edita para cambiar la decisión: se crea uno nuevo que lo reemplaza.
- Sin secretos ni datos reales.
- La decisión tiene que ser verificable: si un gate puede comprobarla (ArchUnit, CI, hook), se nombra en Consecuencias.

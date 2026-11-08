---
description: Agrega una entrada a la bitácora de fallas (docs/bitacora-fallas.md) respetando el formato de tabla.
argument-hint: '[descripción breve de la falla]'
allowed-tools: Read, Edit, Bash(git log:*), Bash(node scripts/fase.mjs:*)
---

Registra una falla en `docs/bitacora-fallas.md`. Descripción del usuario (puede estar vacía): $ARGUMENTS

1. Lee `docs/bitacora-fallas.md` y toma el número siguiente al de la última fila de la tabla.
2. Deriva los campos del contexto de la conversación (gate fallido, error de agente, herramienta ausente, reintento); pregunta solo lo que no puedas deducir:
   - **Fecha**: hoy (`AAAA-MM-DD`).
   - **Fase**: la de `.mada/estado.json` si existe (F0–F6 o C1–C4).
   - **Qué pasó**: síntoma observable, una frase.
   - **Causa**: causa raíz, no el síntoma.
   - **Resolución**: lo que se hizo (o "Pendiente").
   - **Lección para la metodología**: qué debería cambiar en MADA-MB, plantillas o gates.
3. Agrega la fila **al final** de la tabla con el formato exacto:
   `| <#> | <fecha> | <fase> | <qué pasó> | <causa> | <resolución> | <lección> |`
   Escapa los `|` dentro del texto y no uses saltos de línea dentro de una celda.
4. No reescribas ni reordenes filas existentes.
5. Muestra la fila agregada.

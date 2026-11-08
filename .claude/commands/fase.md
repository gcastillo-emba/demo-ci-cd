---
description: Marca el inicio o fin de una fase (F0–F6) o capacidad (C1–C4); registra tiempos en .mada/fases.jsonl y actualiza .mada/estado.json.
argument-hint: 'inicio|fin <F#|Cn>'
allowed-tools: Bash(node scripts/fase.mjs:*)
---

Ejecuta:

```
node scripts/fase.mjs $ARGUMENTS
```

- Muestra la salida del script (evento registrado y, en `fin`, la duración de la fase).
- Si el script falla por argumentos inválidos, muestra el uso: `/fase inicio|fin <F0..F6|C1..C4>`.

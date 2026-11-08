---
description: Corre el gate local MADA-MB (spec, contrato, backend, frontend, evals y opcionalmente E2E) y resume cada paso.
argument-hint: '[--rapido|--e2e]'
allowed-tools: Bash(npm run gate:*), Bash(node scripts/gate.mjs:*)
---

Ejecuta el gate local:

```
node scripts/gate.mjs $ARGUMENTS
```

Luego:

1. Resume el resultado **por paso** en una tabla: `Paso | Resultado (OK/FALLA/OMITIDO) | Duración | Nota`.
2. Si todo pasó, dilo en una línea.
3. Si algún paso falló:
   - Muestra las líneas relevantes del error (no el log completo).
   - Agrega una fila a `docs/bitacora-fallas.md` siguiendo el formato de `/bitacora` (siguiente número, fecha de hoy, fase actual de `.mada/estado.json` si existe).
   - Si es la tercera falla seguida del mismo paso, aplica la regla de parada de `AGENTS.md` §6: no reintentes y pide intervención humana.
4. No desactives ni saltes pasos para que el gate pase.

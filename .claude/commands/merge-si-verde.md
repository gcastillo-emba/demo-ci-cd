---
description: Fusiona un PR por squash solo si ci-ok está en verde y tiene las etiquetas revisor:aprobado y seguridad:ok posteriores al último commit.
argument-hint: '<pr>'
allowed-tools: Bash(node scripts/merge-si-verde.mjs:*)
---

Ejecuta:

```
node scripts/merge-si-verde.mjs $ARGUMENTS
```

- Si el script fusiona, informa el número del PR, el SHA fusionado y la rama.
- Si se niega, informa exactamente qué condición falta (`ci-ok`, `revisor:aprobado`, `seguridad:ok` o etiquetas anteriores al último commit) y **no** intentes fusionar por otra vía (`gh pr merge` directo está bloqueado por el hook `guard-git-push`).
- Si falta `$ARGUMENTS`, pide el número de PR.

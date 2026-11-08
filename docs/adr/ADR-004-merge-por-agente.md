# ADR-004 — Merge por agente con gates verificados

- **Estado:** Aceptado
- **Fecha:** 2026-11-07
- **Decisores:** humano (dueño del POC)
- **Relacionado con:** ADR-002, ADR-003

## Contexto

En MADA-MB el merge a `main` es una decisión humana. Este POC quiere demostrar entrega de punta a punta por agentes, y un humano revisando cada PR anularía ese objetivo. A la vez, GitHub no permite aprobar un PR propio: como todos los PRs los abre la misma cuenta, un ruleset que exija 1 aprobación bloquearía cualquier merge.

## Decisión

**Desviación consciente de MADA-MB:** el agente puede fusionar PRs, pero solo mediante `scripts/merge-si-verde.mjs` (comando `/merge-si-verde`). El script:

1. Lee el PR y su commit de cabeza (`headRefOid`).
2. Exige que el check `ci-ok` de ese commit esté en `SUCCESS`.
3. Exige las etiquetas `revisor:aprobado` y `seguridad:ok`, **aplicadas después del último commit** (compara la fecha del evento `labeled` en la timeline con la fecha del commit de cabeza). Un push posterior invalida las aprobaciones.
4. Ejecuta `gh pr merge --squash --match-head-commit <sha>`, de modo que si llega un commit entre la verificación y el merge, GitHub lo rechaza.

Las etiquetas las ponen subagentes distintos del autor: el revisor nunca revisa trabajo propio y seguridad es de solo lectura más `gh pr comment`/etiquetas. El hook `guard-git-push` bloquea `gh pr merge` fuera del script, pushes a `main`, force push y push de tags.

El ruleset de `main` exige PR con **0 aprobaciones** y el check `ci-ok`; las aprobaciones de juicio viven en las etiquetas, verificadas por el script.

**Excepción:** el tag de release `v0.1.0` lo empuja el humano. El agente lo prepara en local y pide confirmación; ese push dispara `release.yml`.

## Alternativas consideradas

| Alternativa                             | Por qué no                                                                                     |
| --------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Merge humano en cada PR (MADA estándar) | Contradice el objetivo de la demo y frena el flujo.                                            |
| Segunda cuenta o GitHub App que apruebe | Más secretos y administración; la aprobación seguiría siendo automática, solo con otro nombre. |
| Auto-merge nativo de GitHub             | No verifica las etiquetas de juicio ni su vigencia respecto del último commit.                 |
| Ruleset con 1 aprobación                | Imposible: no se puede aprobar un PR propio.                                                   |

## Consecuencias

- **Positivas:** flujo continuo; cada merge deja rastro verificable (check, etiquetas con timestamp, SHA exacto).
- **Negativas:** el control de juicio depende de que los subagentes revisores sean independientes y honestos; un humano con permisos podría poner etiquetas a mano. Es aceptable en un POC con datos sintéticos, no en producción.
- **Gates que la verifican:** `ci-ok` (mecánico), etiquetas vigentes (juicio, verificado mecánicamente), hook `guard-git-push`, `--match-head-commit`.

## Supuestos explícitos

1. La timeline de GitHub expone de forma confiable el momento de cada evento `labeled`.
2. Los subagentes revisor y seguridad corren con contexto separado del autor.
3. El repositorio no maneja datos reales (ADR-005); el riesgo de un merge erróneo está acotado.

## Cómo revertir

Cambiar el ruleset a 1 aprobación con una segunda identidad humana y retirar `/merge-si-verde`; el humano fusiona. No requiere cambios de código fuera de `scripts/` y `.claude/`.

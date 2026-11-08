# ADR-005 — Repositorio público y modo de seguridad

- **Estado:** Aceptado
- **Fecha:** 2026-11-07
- **Decisores:** humano (dueño del repo `gcastillo-emba/demo-ci-cd`)
- **Relacionado con:** ADR-003, ADR-004, bitácora #1

## Contexto

En F0, con el repo privado en plan GitHub Free, la API devolvió 403 para branch protection/rulesets, CodeQL, dependency review y secret scanning (bitácora #1). Son precondición de los gates de seguridad de ADR-002 y del merge por agente de ADR-004. Las opciones eran pagar (Pro/Team + GHAS) o hacer el repo público.

## Decisión

El **2026-11-07** el humano decidió hacer el repositorio **público**. Se justifica porque:

- Todos los datos son **100 % sintéticos** (clientes, asuntos, abogados y horas ficticios; usuarios de demo generados). No hay información de clientes reales ni privilegiada.
- No hay secretos en el repo: `.env*` está bloqueado por el hook `guard-secrets` (salvo `.env.example`) y el secreto JWT de desarrollo solo existe como default en compose (ADR-006).

Controles que se activan:

| Control                                                                      | Dónde                             |
| ---------------------------------------------------------------------------- | --------------------------------- |
| Ruleset en `main` (PR, `ci-ok`, sin force push ni borrado, historial lineal) | Configuración del repo            |
| Secret scanning + **push protection**                                        | Configuración del repo (`gh api`) |
| CodeQL `security-extended` (Java y TS)                                       | `security.yml`                    |
| Dependency review (bloquea `high`)                                           | `security.yml`                    |
| gitleaks en CI                                                               | `security.yml` y CI mínimo de F1  |
| Dependabot (versiones y alertas)                                             | `dependabot.yml` + configuración  |
| Solo squash merge                                                            | Configuración del repo            |
| Paquetes GHCR públicos                                                       | Configuración del paquete         |

Antes del primer push se corre un escaneo de secretos local sobre todo el historial; recién después se cambia la visibilidad con `gh repo edit --visibility public`.

## Alternativas consideradas

| Alternativa                                   | Por qué no                                                                           |
| --------------------------------------------- | ------------------------------------------------------------------------------------ |
| Pagar Pro/Team + GHAS                         | Costo recurrente para un POC; requiere aprobación de presupuesto (parada del brief). |
| Seguir privado sin esos gates                 | Elimina los gates de seguridad y el ruleset que habilitan ADR-004.                   |
| Gates equivalentes solo en CI (Semgrep, etc.) | No hay forma de imponer un check requerido sin ruleset.                              |

## Consecuencias

- **Positivas:** ruleset, CodeQL, dependency review, secret scanning y minutos de Actions gratis; el POC sirve como material de referencia.
- **Negativas:** el código y la bitácora son visibles para cualquiera; un secreto filtrado sería público al instante (lo mitigan push protection y gitleaks); patrón no trasladable a proyectos con datos reales del estudio.
- **Gates que la verifican:** push protection, gitleaks, revisión de seguridad (`seguridad:ok`).

## Supuestos explícitos

1. Ningún dato real de la firma ni de sus clientes se introducirá en el repo, ni siquiera en fixtures o capturas de `docs/evidence/`.
2. El nombre y la estructura del POC no revelan información sensible de la organización.
3. GitHub mantiene gratuitas estas funciones para repos públicos.

## Cómo revertir

`gh repo edit --visibility private`. Se pierden de inmediato el ruleset y los gates GHAS en plan Free, lo que obliga a revisar ADR-003 y ADR-004. Lo ya publicado debe considerarse expuesto.

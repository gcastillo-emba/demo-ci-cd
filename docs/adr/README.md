# Registro de decisiones de arquitectura (ADR)

Cada decisión estructural del POC "Asuntos & Horas" queda como un ADR numerado. Se usa [`plantilla.md`](plantilla.md). Un ADR aceptado no cambia su decisión: si la decisión cambia, se reemplaza por uno nuevo que lo declare en su estado. Se admiten **enmiendas** que no cambian la decisión (precisiones de implementación, desviaciones documentadas, controles agregados), siempre con una línea `Revisado: AAAA-MM-DD (PR #N): <qué se enmendó>` en la cabecera. Las enmiendas también pasan por la skill `adr` (bitácora, fila 50).

Numeración: `ADR-NNN-titulo-en-kebab-case.md`, correlativa, sin huecos. La skill `adr` asigna el siguiente número de forma automática.

| #                                                        | Título                                  | Estado   | Fecha      |
| -------------------------------------------------------- | --------------------------------------- | -------- | ---------- |
| [ADR-001](ADR-001-arquitectura-hexagonal-y-api-first.md) | Arquitectura hexagonal y API-first      | Aceptado | 2026-11-07 |
| [ADR-002](ADR-002-estrategia-de-pruebas-y-arnes.md)      | Estrategia de pruebas y arnés local     | Aceptado | 2026-11-07 |
| [ADR-003](ADR-003-ci-cd.md)                              | CI/CD con GitHub Actions y GHCR         | Aceptado | 2026-11-07 |
| [ADR-004](ADR-004-merge-por-agente.md)                   | Merge por agente con gates verificados  | Aceptado | 2026-11-07 |
| [ADR-005](ADR-005-repo-publico-y-modo-de-seguridad.md)   | Repositorio público y modo de seguridad | Aceptado | 2026-11-07 |
| [ADR-006](ADR-006-autenticacion-jwt.md)                  | Autenticación con JWT HS256             | Aceptado | 2026-11-07 |

Fuente de verdad de las decisiones: [`docs/plan-maestro.md`](../plan-maestro.md). Fallas y desvíos: [`docs/bitacora-fallas.md`](../bitacora-fallas.md).

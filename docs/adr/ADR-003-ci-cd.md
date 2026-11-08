# ADR-003 — CI/CD con GitHub Actions y GHCR

- **Estado:** Aceptado
- **Fecha:** 2026-11-07
- **Revisado:** 2026-11-08 (PR #14): enmienda sin cambio de decisión. Restringe `claude-review.yml` por autor y documenta la desviación de F3 (el job `changes` no se implementó).
- **Decisores:** humano + arquitecto (diseño de devops)
- **Relacionado con:** ADR-002, ADR-004, ADR-005

## Contexto

Los gates mecánicos de ADR-002 deben correr en cada PR y decidir el merge sin intervención humana (ADR-004). El monorepo mezcla Java, TypeScript, specs y contrato: correr todo en cada cambio es lento, pero marcar como requeridos jobs condicionales hace que los PRs queden "esperando" checks que nunca corren. Además se quiere entregar imágenes listas para una demo en menos de 5 minutos.

## Decisión

Todas las acciones se fijan por **SHA** (comentario con la versión), cada workflow declara `permissions` mínimos y cada job `timeout-minutes`.

| Workflow            | Disparador                                | Qué hace                                                                                                                                                                                                                                                                                                                                                                  |
| ------------------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ci.yml`            | PR y push a `main`                        | `changes` (paths-filter) → `spec` · `contract` (`api:lint` + `api:check`) · `backend` (`mvnw verify`: unit + ArchUnit + Testcontainers + JaCoCo + `spotless:check`) · `frontend` (lint, `prettier --check`, typecheck, Vitest) · `evals` (fake) · `e2e` (build con caché `gha` → `compose up --wait` → Playwright → logs y artefactos si falla → `down -v`) → **`ci-ok`** |
| `security.yml`      | PR, push a `main`, semanal                | CodeQL (`java-kotlin`, `javascript-typescript`, `security-extended`) + dependency-review (falla en `high`) + gitleaks                                                                                                                                                                                                                                                     |
| `pr-title.yml`      | PR                                        | Título Conventional Commits con scopes `c1\|c2\|c3\|c4\|api\|spec\|ci\|docs\|deps` (el repo solo permite squash, el título es el commit)                                                                                                                                                                                                                                  |
| `cd.yml`            | `workflow_run` de CI exitoso sobre `main` | Publica `ghcr.io/gcastillo-emba/demo-ci-cd-{backend,frontend}` con tags `sha-<short>`, `main`, `latest`; amd64; build attestations; paquetes públicos                                                                                                                                                                                                                     |
| `release.yml`       | Push de tag `v*`                          | Re-etiqueta las imágenes `sha-*` del commit (sin rebuild), genera notas con git-cliff (`cliff.toml`, secciones en español) y ejecuta `gh release create` adjuntando `docker-compose.demo.yml` y el runbook                                                                                                                                                                |
| `claude-review.yml` | PR                                        | Revisión con Claude; **inerte** si falta el secreto `ANTHROPIC_API_KEY` o `vars.CLAUDE_REVIEW_ENABLED != 'true'`. Solo para autores `OWNER`/`MEMBER`/`COLLABORATOR`; el prompt se lee de la rama base; la clave solo llega al paso de la acción                                                                                                                           |
| `evals-real.yml`    | `workflow_dispatch`                       | Evals contra el modelo real; inerte con el mismo guard                                                                                                                                                                                                                                                                                                                    |
| `dependabot.yml`    | Semanal                                   | maven, npm y github-actions; agrupado; ignora majors de Spring Boot                                                                                                                                                                                                                                                                                                       |

**`ci-ok` es el único check requerido** por el ruleset. Depende de todos los jobs con `if: always()` y falla si alguno terminó en `failure` o `cancelled`; un job `skipped` por paths-filter cuenta como éxito. Así el ruleset no cambia cuando se agregan jobs.

**Ruleset en `main`:** PR obligatorio con 0 aprobaciones (ver ADR-004), `ci-ok` requerido, sin force push ni borrado, historial lineal, solo squash.

En F1 se entrega un CI mínimo (`spec` + gitleaks + `ci-ok`); el resto llega en F3 (walking skeleton).

**Desviación (F3): el job `changes` (paths-filter) no se implementó.** Todos los jobs de `ci.yml` corren en cada PR y push a `main`. Motivo: el repo es público (ADR-005), con minutos de Actions ilimitados, y sin filtro de rutas el pipeline es más simple: no hay jobs `skipped` que interpretar en `ci-ok` ni riesgo de omitir un gate por un filtro mal escrito. El costo es que un PR de solo docs tarda lo mismo que uno de código. Se reconsidera si el tiempo de CI se vuelve un cuello de botella (señalado en la revisión del PR #5).

## Alternativas consideradas

| Alternativa                    | Por qué no                                                                             |
| ------------------------------ | -------------------------------------------------------------------------------------- |
| Marcar cada job como requerido | Jobs omitidos por paths-filter bloquean el PR; el ruleset se edita con cada job nuevo. |
| CD en el mismo `ci.yml`        | Mezcla permisos de escritura de paquetes con código de PR no confiable.                |
| Rebuild de imágenes en release | La imagen liberada no sería la probada; re-etiquetar garantiza el mismo digest.        |
| Docker Hub                     | Requiere otra cuenta y secreto; GHCR usa `GITHUB_TOKEN`.                               |
| release-please                 | Abre PRs propios y agrega un flujo extra; git-cliff sobre Conventional Commits basta.  |

## Consecuencias

- **Positivas:** un solo check que gobernar; PRs de solo docs son rápidos; lo liberado es exactamente lo probado en `main`; supply chain endurecida (SHA, attestations, permisos mínimos).
- **Negativas:** fijar por SHA exige dependabot para no quedar desactualizado; `workflow_run` es más difícil de depurar; E2E en CI tarda varios minutos.
- **Gates que la verifican:** el propio ruleset; `pr-title`; revisión de seguridad de los workflows (juicio).

## Supuestos explícitos

1. El repo es público (ADR-005): minutos ilimitados, CodeQL y dependency-review disponibles.
2. `GITHUB_TOKEN` con `packages: write` alcanza para publicar en GHCR y hacer públicos los paquetes (la visibilidad se ajusta una vez, a mano o vía `gh api`).
3. Los runners `ubuntu-latest` tienen Docker y recursos para compose + Playwright.

## Cómo revertir

Quitar `ci-ok` del ruleset y requerir jobs individuales, o desactivar workflows con `gh workflow disable`. Para volver a rebuild en release, reemplazar el paso de re-etiquetado por build + push. Afecta ADR-004 (el script de merge consulta `ci-ok`).

# Asuntos & Horas

[![CI](https://github.com/gcastillo-emba/demo-ci-cd/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/gcastillo-emba/demo-ci-cd/actions/workflows/ci.yml)
[![Security (CodeQL)](https://github.com/gcastillo-emba/demo-ci-cd/actions/workflows/security.yml/badge.svg?branch=main)](https://github.com/gcastillo-emba/demo-ci-cd/actions/workflows/security.yml)
[![CD](https://github.com/gcastillo-emba/demo-ci-cd/actions/workflows/cd.yml/badge.svg)](https://github.com/gcastillo-emba/demo-ci-cd/actions/workflows/cd.yml)

POC de **registro de horas para una firma legal**: clientes, asuntos, registro y aprobación de horas, tablero y un asistente de descripciones. Se construyó **de punta a punta con agentes de Claude Code** siguiendo la metodología **MADA-MB** (spec-driven con OpenSpec, gates mecánicos y de juicio, bitácora de fallas), con CI/CD completo en GitHub Actions e imágenes publicadas en GHCR.

> Datos **100 % sintéticos**: ningún cliente, abogado ni asunto es real. Secretos y contraseñas solo de demo.

## Arranque en menos de 5 minutos

Solo hace falta Docker con Compose v2 y el archivo [`docker-compose.demo.yml`](docker-compose.demo.yml) (no se compila nada):

```bash
docker compose -f docker-compose.demo.yml up -d --wait
```

Abrir **http://localhost:8080** e ingresar con un usuario de demo. Para una release concreta: `TAG=v0.1.0 docker compose -f docker-compose.demo.yml up -d --wait`. Para bajarlo y borrar los datos: `docker compose -f docker-compose.demo.yml down -v`.

**Tiempo medido** (2026-11-08, Windows 11 + Docker Desktop, `npm run demo:check`): **87,5 s** en frío (descarga de las imágenes + arranque hasta `healthy`) y **46,6 s** con las imágenes ya descargadas. Verificación, actualización, release, rollback y solución de problemas: [runbook de demo](docs/runbook.md).

### Usuarios de demo

Todos ficticios; contraseña común **`Demo2026!`** (solo demo).

| Usuario                 | Nombre       | Rol                |
| ----------------------- | ------------ | ------------------ |
| `socia@asuntos.test`    | Socia Demo   | `PARTNER` (SOCIO)  |
| `abogado1@asuntos.test` | Abogado Uno  | `LAWYER` (ABOGADO) |
| `abogado2@asuntos.test` | Abogada Dos  | `LAWYER` (ABOGADO) |
| `abogado3@asuntos.test` | Abogado Tres | `LAWYER` (ABOGADO) |

## Construir desde el código

Requisitos: Node 22, Java 21 (el wrapper Maven viene en el repo) y Docker.

```bash
npm ci
docker compose up -d --build --wait     # stack completo construido localmente → http://localhost:8080
docker compose down -v
```

Modo desarrollo:

```bash
npm run dev     # PostgreSQL en Docker, backend en :8081 (perfil demo) y frontend Vite en :5173
```

## Capacidades

| Capacidad                       | Cambio OpenSpec             | Estado                                                                  |
| ------------------------------- | --------------------------- | ----------------------------------------------------------------------- |
| Walking skeleton (login, JWT)   | — (F3)                      | Fusionado ([#5](https://github.com/gcastillo-emba/demo-ci-cd/pull/5))   |
| C1 — Clientes y asuntos         | `add-clients-matters`       | Fusionado ([#10](https://github.com/gcastillo-emba/demo-ci-cd/pull/10)) |
| C2 — Registro de horas          | `add-time-entries`          | En construcción                                                         |
| C3 — Tablero de horas           | `add-hours-dashboard`       | Propuesta aprobada (F2), pendiente de implementar                       |
| C4 — Asistente de descripciones | `add-description-assistant` | Propuesta aprobada (F2), pendiente de implementar                       |

## Estructura del repositorio

| Ruta                      | Contenido                                                                            |
| ------------------------- | ------------------------------------------------------------------------------------ |
| `api/openapi.yaml`        | Contrato OpenAPI: única fuente de la API (API-first).                                |
| `backend/`                | Spring Boot 3.5 + Java 21, arquitectura hexagonal, Flyway, PostgreSQL 17.            |
| `frontend/`               | React 19 + Vite + TypeScript con cliente generado del contrato; servido por nginx.   |
| `e2e/`                    | Pruebas E2E con Playwright y generación de evidencia visual.                         |
| `openspec/`               | Specs vigentes (`specs/`) y cambios en curso o archivados (`changes/`).              |
| `docs/`                   | Plan maestro, ADRs, arquitectura C4, glosario, bitácora, runbook y evidencia.        |
| `scripts/`                | Arnés en Node: gate local, E2E, merge del agente, candados entre worktrees, demo.    |
| `.claude/`                | Capa de Claude Code: subagentes, hooks, skills y comandos.                           |
| `.github/workflows/`      | CI, Security (CodeQL, dependency-review, gitleaks), CD, Release y workflows inertes. |
| `docker-compose.yml`      | Stack construido desde el código.                                                    |
| `docker-compose.demo.yml` | Stack de demo con imágenes publicadas en GHCR.                                       |

## Arnés y comandos

| Propósito                                       | Comando                                                         |
| ----------------------------------------------- | --------------------------------------------------------------- |
| Gate local (spec, contrato, backend, frontend…) | `npm run gate` · sin Testcontainers: `npm run gate -- --rapido` |
| Gate local con E2E sobre compose                | `npm run gate -- --e2e`                                         |
| Validar specs                                   | `npm run spec:validate`                                         |
| Contrato: lint y cliente sin drift              | `npm run api:lint` · `npm run api:check`                        |
| E2E                                             | `npm run e2e`                                                   |
| Verificación cronometrada de la demo            | `npm run demo:check`                                            |
| Resumen headless de la bitácora (`claude -p`)   | `npm run bitacora:resumen`                                      |

Lista completa en [`CLAUDE.md`](CLAUDE.md). El merge a `main` solo ocurre con `scripts/merge-si-verde.mjs`, que exige el check `ci-ok` en verde y la aprobación de los gates de juicio.

## Metodología MADA-MB (resumen)

Cada cambio recorre **Research → Plan → Implement → Verificar → Revisar → Merge** ([`AGENTS.md`](AGENTS.md)):

- **Spec-driven**: nada se implementa sin un cambio OpenSpec aprobado (`proposal.md`, specs con DEBE/NO DEBE y escenarios Dado/Cuando/Entonces, `design.md` con supuestos, `tasks.md`).
- **Gates mecánicos** que nunca se relajan: validación OpenSpec, lint de contrato, compilación contra interfaces generadas, tests, ArchUnit, JaCoCo ≥ 80 %, formato, E2E, gitleaks y CodeQL, agregados en el check `ci-ok`.
- **Gates de juicio** independientes del autor: subagentes `revisor` y `seguridad`, con etiquetas en el PR.
- **Roles con rutas propias** (arquitecto, redactor-specs, dev-backend, dev-frontend, qa, devops), verificadas por `scripts/check-ownership.mjs`; backend y frontend trabajan en paralelo en worktrees aislados.
- **Bitácora de fallas**: toda falla de gate, de agente o de herramienta queda registrada con causa, resolución y lección.

## Documentación

| Documento                                               | Qué contiene                                                                   |
| ------------------------------------------------------- | ------------------------------------------------------------------------------ |
| [Plan maestro](docs/plan-maestro.md)                    | Fuente de verdad: alcance, arquitectura, CI/CD, fases y verificación.          |
| [ADRs](docs/adr/README.md)                              | Decisiones de arquitectura (hexagonal, pruebas, CI/CD, merge, seguridad, JWT). |
| [Arquitectura C4](docs/arquitectura/c4-contenedores.md) | Diagramas de contexto y contenedores.                                          |
| [Glosario](docs/glosario.md)                            | Términos del dominio en español e inglés.                                      |
| [Bitácora de fallas](docs/bitacora-fallas.md)           | Registro de fallas y lecciones.                                                |
| [Resumen de la bitácora](docs/bitacora-resumen.md)      | Resumen ejecutivo generado en modo headless con `claude -p`.                   |
| [Runbook de demo](docs/runbook.md)                      | Despliegue, verificación, release, rollback y solución de problemas.           |
| Informe de la demo (`docs/informe-demo.md`)             | Matriz de capacidades de Claude Code: dónde se usó y resultado (en F6).        |
| Métricas de la demo (`docs/metricas-demo.md`)           | PRs, corridas de CI y tiempos por fase (en F6).                                |

## Licencia

Sin licencia: es un POC interno de demostración. No se concede permiso de uso, copia ni distribución.

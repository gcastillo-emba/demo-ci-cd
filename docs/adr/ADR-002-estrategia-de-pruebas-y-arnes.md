# ADR-002 — Estrategia de pruebas y arnés local

- **Estado:** Aceptado
- **Fecha:** 2026-11-07
- **Decisores:** humano + arquitecto
- **Relacionado con:** ADR-001, ADR-003, ADR-004

## Contexto

MADA-MB separa **gates mecánicos** (deterministas, los ejecuta una máquina) de **gates de juicio** (requieren criterio: revisor, seguridad, humano). Con agentes que escriben a la vez código y tests, el riesgo es que los tests sean complacientes o lentos. Se necesita una pirámide con responsabilidades claras, un umbral de cobertura donde importa y un único comando local equivalente a CI.

## Decisión

### Pirámide

| Nivel           | Qué cubre                                                                                                   | Herramientas                                                                        | Dónde corre             |
| --------------- | ----------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | ----------------------- |
| Unit de dominio | Reglas: 24 h por día (PENDING + APPROVED), ventana de edición de 7 días, estados terminales, décimas 1..240 | JUnit 5, AssertJ, `Clock.fixed` en `America/La_Paz`                                 | `mvnw verify`           |
| Aplicación      | Casos de uso y autorización por rol y por propiedad                                                         | Fakes en memoria de los puertos `out` (sin Mockito para repositorios)               | `mvnw verify`           |
| Arquitectura    | Reglas de ADR-001                                                                                           | ArchUnit                                                                            | `mvnw verify`           |
| Integración web | HTTP → DB real, Problem Details, seguridad, concurrencia (`pg_advisory_xact_lock`), conformidad de contrato | Spring Boot Test + Testcontainers `postgres:17-alpine` (misma imagen que compose)   | `mvnw verify`           |
| Cobertura       | **JaCoCo ≥ 80 %** de líneas en `domain` + `application` (adaptadores fuera del umbral)                      | `jacoco-maven-plugin` goal `check`                                                  | `mvnw verify`           |
| Frontend        | Componentes, formularios, estados de error, accesibilidad básica                                            | Vitest + Testing Library (jsdom)                                                    | `npm run frontend:test` |
| E2E             | Flujos críticos por capacidad contra el stack real                                                          | Playwright contra `docker compose up --wait`                                        | `npm run e2e`           |
| Evals           | Guardrails y comportamiento del Asistente de descripción                                                    | 10 casos YAML en `evals/description-assistant/cases/`, adaptador `fake` (`-Pevals`) | `npm run evals`         |

Reglas transversales: nada de `Thread.sleep`; fechas siempre vía `Clock`; datos de prueba sintéticos; cada slug de error tiene al menos un test que lo produce; el adaptador Anthropic se prueba con HTTP simulado.

### Arnés local

`npm run gate` (o `/gate` en Claude Code) ejecuta `scripts/gate.mjs`, que corre en orden spec → contrato → backend → frontend → evals; con `--e2e` agrega E2E sobre compose y con `--rapido` omite Testcontainers y E2E para iterar. Registra tiempos y resultado en `.mada/` y cada fallo en `.mada/fallas.jsonl`; el hook `bitacora-on-stop` avisa si falta reflejarlo en la bitácora. Usa los mismos comandos que CI: si `gate --e2e` pasa en Windows, CI debe pasar en Linux.

### Gates mecánicos vs de juicio

| Gate                                                | Tipo     | Ejecutor                              | ¿Bloquea merge?                        |
| --------------------------------------------------- | -------- | ------------------------------------- | -------------------------------------- |
| `openspec validate --all --strict --no-interactive` | Mecánico | CI `spec` / gate                      | Sí (vía `ci-ok`)                       |
| Lint del contrato + `api:check`                     | Mecánico | CI `contract`                         | Sí                                     |
| Unit, ArchUnit, ITs, JaCoCo, Spotless               | Mecánico | CI `backend`                          | Sí                                     |
| ESLint, Prettier, typecheck, Vitest                 | Mecánico | CI `frontend`                         | Sí                                     |
| Evals (fake)                                        | Mecánico | CI `evals`                            | Sí                                     |
| E2E Playwright                                      | Mecánico | CI `e2e`                              | Sí                                     |
| CodeQL, dependency-review, gitleaks                 | Mecánico | `security.yml`                        | Una alerta high bloquea `seguridad:ok` |
| Hooks `guard-secrets`, `guard-git-push`             | Mecánico | Claude Code local                     | Bloquea la acción del agente           |
| Trazabilidad spec ↔ brief, calidad de tests        | Juicio   | revisor → etiqueta `revisor:aprobado` | Sí (ADR-004)                           |
| Revisión de seguridad del diff                      | Juicio   | seguridad → etiqueta `seguridad:ok`   | Sí (ADR-004)                           |
| Push del tag de release `v0.1.0`                    | Juicio   | humano                                | —                                      |

## Alternativas consideradas

| Alternativa                      | Por qué no                                                                    |
| -------------------------------- | ----------------------------------------------------------------------------- |
| H2 en las pruebas de integración | Diverge de PostgreSQL (advisory locks, CHECK, tipos); da falsos verdes.       |
| Mockito para repositorios        | Acopla los tests a la implementación; los fakes son reutilizables y legibles. |
| Cobertura global ≥ 80 %          | Premia testear adaptadores triviales; el valor está en dominio + aplicación.  |
| Cypress                          | Playwright tiene mejores trazas, multi-navegador y MCP para agentes.          |
| Evals solo contra el modelo real | No hay `ANTHROPIC_API_KEY` (bitácora #2); el fake hace el gate determinista.  |

## Consecuencias

- **Positivas:** fallas rápidas y localizadas; paridad local/CI; los agentes tienen un único comando de "terminado".
- **Negativas:** `mvnw verify` con Testcontainers tarda minutos y Docker tiene 8 GB, así que las verificaciones pesadas se serializan; E2E es el gate más frágil.
- **Gates que la verifican:** la tabla anterior.

## Supuestos explícitos

1. Docker está disponible en local y en los runners `ubuntu-latest`.
2. El 80 % es alcanzable sin tests artificiales en un dominio de este tamaño.
3. Los evals con fake detectan regresiones de guardrails, no de calidad del modelo real (eso lo cubre `evals-real.yml`, manual).

## Cómo revertir

Bajar el umbral en el POM, sacar un job de las dependencias de `ci-ok` o quitar `--e2e` del gate. Toda relajación se registra en la bitácora con su motivo y, si es permanente, con un ADR que reemplace a este.

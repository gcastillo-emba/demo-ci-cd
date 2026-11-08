# ADR-001 — Arquitectura hexagonal y API-first

- **Estado:** Aceptado
- **Fecha:** 2026-11-07
- **Decisores:** humano (dueño del POC) + arquitecto (agente)
- **Relacionado con:** ADR-002, ADR-006, `docs/plan-maestro.md` § Decisiones técnicas clave

## Contexto

El POC lo construyen agentes en paralelo (dev-backend y dev-frontend en worktrees aislados). Sin fronteras mecánicas, cada agente "acomoda" el código donde le conviene y el drift entre backend, frontend y contrato aparece tarde, en E2E. Se necesita (a) un dominio aislado y testeable sin infraestructura y (b) un contrato único que rompa la compilación de ambos lados ante cualquier divergencia.

## Decisión

### Hexagonal (ports & adapters)

Un solo módulo Maven con paquete raíz `com.emba.asuntoshoras`:

| Paquete                                   | Contenido                                                                                       | Puede depender de                |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------- | -------------------------------- |
| `domain`                                  | Entidades, value objects (`MatterCode`, horas en décimas), reglas, `DomainException`            | nada (solo JDK)                  |
| `application.port.in`                     | Casos de uso (interfaces)                                                                       | `domain`                         |
| `application.port.out`                    | Puertos de salida: repositorios, `DescriptionAssistant`, hash de contraseñas, emisión de tokens | `domain`                         |
| `application.service`                     | Implementación de casos de uso; autorización por rol y por propiedad                            | `domain`, `application.port.*`   |
| `adapters.in.web`                         | Controllers que implementan las interfaces generadas; mapeo a Problem Details                   | `application.port.in`            |
| `adapters.out.persistence`                | JPA `@Entity`, repositorios Spring Data, Flyway                                                 | `application.port.out`, `domain` |
| `adapters.out.security`                   | JWT Nimbus, BCrypt, filtro de Spring Security                                                   | `application.port.out`           |
| `adapters.out.assistant.{fake,anthropic}` | Implementaciones de `DescriptionAssistant`                                                      | `application.port.out`           |
| `config`                                  | Wiring de Spring, `Clock`, indicadores de funcionalidad                                         | todo                             |

**Excepción documentada:** `@Transactional` (Spring) se permite en `application.service`. Es la única anotación de framework tolerada fuera de adaptadores y `config`; moverla a un decorador agregaría código sin valor en un POC. La regla ArchUnit correspondiente la lista como excepción explícita.

**Reglas ArchUnit** (gate mecánico dentro de `mvnw verify`):

1. Capas onion: `domain` ← `application` ← `adapters`; ningún adaptador depende de otro adaptador.
2. `domain` no depende de Spring, JPA, Jackson ni de ningún otro paquete del proyecto.
3. `@Entity` solo en `adapters.out.persistence`; `@RestController` solo en `adapters.in.web`.
4. Nadie llama a `LocalDate.now()`, `Instant.now()` y similares sin un `Clock` inyectado.
5. Sin inyección por campo (`@Autowired` sobre atributos).
6. El cliente de Anthropic solo se usa en `adapters.out.assistant.anthropic`.
7. Cada subclase de `DomainException` tiene entrada en `ProblemCatalog` (slug, status, título).

### API-first (contract-first)

- `api/openapi.yaml` (OpenAPI 3.1) es la **única fuente** del contrato. Se lintea con `@redocly/cli` (`npm run api:lint`).
- **Servidor:** `openapi-generator-maven-plugin`, generador `spring`, `interfaceOnly=true`. Los controllers implementan las interfaces generadas: un endpoint renombrado o un DTO cambiado rompe la compilación. `ContractConformanceIT` verifica además que el conjunto de endpoints expuestos sea igual al del contrato.
- **Cliente:** `openapi-typescript` genera `frontend/src/api/generated/schema.ts` (versionado) y `openapi-fetch` lo consume con tipos. `scripts/api-client.mjs --check` (en `npm run api:check` y en CI) regenera en memoria y falla si difiere, normalizando CRLF.
- springdoc solo sirve Swagger UI sobre el YAML estático; nunca genera el contrato desde el código.
- Orden de trabajo por capacidad: contrato + cliente generado se commitean primero en `feat/cN`; después backend y frontend avanzan en paralelo.

## Alternativas consideradas

| Alternativa                                               | Por qué no                                                                                            |
| --------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| Capas clásicas (controller/service/repository)            | Sin frontera verificable; el dominio termina acoplado a JPA.                                          |
| Multi-módulo Maven por capa                               | Frontera más fuerte en compilación, pero triplica POMs y tiempos; ArchUnit da la garantía suficiente. |
| Code-first (springdoc genera el contrato)                 | El contrato pasa a ser un subproducto; el frontend no puede empezar en paralelo.                      |
| Cliente completo con openapi-generator `typescript-fetch` | Más código generado y runtime propio; openapi-fetch es mínimo y solo aporta tipos.                    |

## Consecuencias

- **Positivas:** dominio probado sin Spring y con `Clock.fixed`; paralelismo real entre agentes; el drift se detecta en compilación y en `api:check`, no en E2E.
- **Negativas:** más mapeo DTO ↔ dominio ↔ entidad; el generador impone convenciones de nombres; `schema.ts` versionado produce diffs ruidosos.
- **Gates que la verifican:** ArchUnit, compilación contra interfaces, `ContractConformanceIT`, `api:lint`, `api:check` (mecánicos); el revisor valida que el contrato refleje la spec (juicio).

## Supuestos explícitos

1. openapi-generator soporta OpenAPI 3.1 lo suficiente para las estructuras del POC (sin `oneOf` complejos).
2. Un solo módulo con ArchUnit basta como frontera; nadie desactiva reglas con `@ArchIgnore` sin un ADR.
3. El volumen de endpoints (menos de 25) hace aceptable el mapeo manual sin MapStruct.

## Cómo revertir

- Hexagonal → capas: fusionar `port.*` en los servicios y retirar las reglas ArchUnit 1–3. Costo bajo; se pierde el aislamiento del dominio.
- API-first → code-first: quitar el plugin, anotar controllers con springdoc y generar el YAML en el build; `schema.ts` pasaría a generarse desde ese artefacto. Costo medio; afecta el job `contract` de ADR-003.

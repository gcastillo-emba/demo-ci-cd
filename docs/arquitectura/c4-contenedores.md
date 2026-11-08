# C4 nivel 2 — Contenedores

Contenedores desplegables de "Asuntos & Horas" tal como los levanta `docker compose` (y `docker-compose.demo.yml` desde GHCR).

```mermaid
flowchart TB
    socio["<b>Socio</b> (PARTNER)<br/>[Persona]"]
    abogado["<b>Abogado</b> (LAWYER)<br/>[Persona]"]

    subgraph sistema["Asuntos & Horas [Sistema]"]
        direction TB
        spa["<b>SPA</b><br/>[Contenedor: React + Vite + TS, servido por nginx]<br/>UI en español; cliente tipado openapi-fetch;<br/>JWT en sessionStorage.<br/><b>Puerto 8080 (publicado)</b>"]
        api["<b>API</b><br/>[Contenedor: Spring Boot 3.5, Java 21]<br/>Hexagonal; implementa api/openapi.yaml;<br/>JWT HS256; Problem Details RFC 9457;<br/>Actuator en /api/actuator.<br/><b>Puerto 8081 (solo red interna)</b>"]
        db[("<b>Base de datos</b><br/>[Contenedor: PostgreSQL 17 alpine]<br/>Flyway V1..V3 + seed R__ (perfil demo).<br/>Puerto 5432 (solo red interna)")]
    end

    anthropic["<b>Anthropic API</b><br/>[Sistema externo, opcional]"]

    socio -- "HTTPS :8080" --> spa
    abogado -- "HTTPS :8080" --> spa
    spa -- "Proxy nginx /api/* → api:8081<br/>(mismo origen, sin CORS)" --> api
    api -- "JDBC (HikariCP)" --> db
    api -. "HTTPS/JSON con timeout (java.net.http),<br/>solo si app.features.description-assistant<br/>y adaptador anthropic" .-> anthropic

    classDef person fill:#08427b,color:#fff,stroke:#052e56
    classDef container fill:#438dd5,color:#fff,stroke:#2e6295
    classDef external fill:#999,color:#fff,stroke:#6b6b6b
    class socio,abogado person
    class spa,api,db container
    class anthropic external
```

## Contenedores

| Contenedor     | Tecnología                       | Puerto                     | Responsabilidad                                                                   |
| -------------- | -------------------------------- | -------------------------- | --------------------------------------------------------------------------------- |
| SPA (frontend) | React + Vite + TypeScript, nginx | **8080** publicado al host | Sirve estáticos y hace proxy de `/api` al backend. Único punto de entrada.        |
| API (backend)  | Spring Boot 3.5.x, Java 21       | **8081** interno           | Casos de uso, reglas de negocio, autenticación y autorización, contrato OpenAPI.  |
| Base de datos  | `postgres:17-alpine`             | 5432 interno               | Persistencia; misma imagen que Testcontainers. Horas en décimas (`hours_tenths`). |

## Notas

- **Mismo origen:** el navegador solo habla con nginx en `:8080`; `/api/*` se reenvía a `api:8081`. No se configura CORS.
- **Healthchecks en cadena:** db (`pg_isready`) → api (`/api/actuator/health`) → spa. `compose up --wait` los usa en E2E y CI.
- **Imágenes:** `ghcr.io/gcastillo-emba/demo-ci-cd-backend` y `-frontend` (ADR-003).
- **Secretos:** `APP_JWT_SECRET` por variable de entorno; default de desarrollo solo en compose (ADR-006).
- **Asistente de descripción:** el flag se expone en `GET /api/features`; con el flag apagado la UI oculta la funcionalidad.
- Contexto: [c4-contexto.md](c4-contexto.md).

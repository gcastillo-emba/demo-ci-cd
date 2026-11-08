# C4 nivel 1 — Contexto del sistema

Vista de quién usa "Asuntos & Horas" y con qué sistemas externos se relaciona. Datos 100 % sintéticos (ADR-005).

```mermaid
flowchart TB
    socio["<b>Socio</b> (PARTNER)<br/>[Persona]<br/>Gestiona clientes y asuntos,<br/>aprueba o rechaza horas,<br/>consulta el tablero"]
    abogado["<b>Abogado</b> (LAWYER)<br/>[Persona]<br/>Registra y edita sus horas,<br/>pide sugerencias de descripción"]
    dev["<b>Equipo de desarrollo</b><br/>[Persona + agentes Claude Code]<br/>Especifica, construye y libera"]

    sistema["<b>Asuntos & Horas</b><br/>[Sistema de software]<br/>Registro de horas por asunto<br/>para una firma legal (POC)"]

    anthropic["<b>Anthropic API</b><br/>[Sistema externo, opcional]<br/>Modelo para el Asistente de descripción.<br/>Detrás del flag description-assistant;<br/>por defecto se usa el adaptador fake"]
    github["<b>GitHub / GHCR</b><br/>[Sistema externo]<br/>Código, CI/CD (Actions),<br/>registro de imágenes de contenedor"]

    socio -- "Usa (HTTPS, navegador)" --> sistema
    abogado -- "Usa (HTTPS, navegador)" --> sistema
    sistema -. "Pide sugerencias (HTTPS/JSON),<br/>solo si el flag está activo" .-> anthropic
    dev -- "Push, PRs, tags" --> github
    github -- "Publica imágenes<br/>backend y frontend" --> sistema

    classDef person fill:#08427b,color:#fff,stroke:#052e56
    classDef system fill:#1168bd,color:#fff,stroke:#0b4884
    classDef external fill:#999,color:#fff,stroke:#6b6b6b
    class socio,abogado,dev person
    class sistema system
    class anthropic,github external
```

## Notas

- **Actores:** dos roles de negocio (Socio y Abogado). No hay acceso anónimo salvo el login.
- **Anthropic API** es opcional: sin `ANTHROPIC_API_KEY` (bitácora #2) el sistema funciona completo con el adaptador `fake` determinista. Nunca se envía PII; la entrada se limita a 1–500 caracteres.
- **GitHub/GHCR** no es dependencia en tiempo de ejecución: provee las imágenes que `docker-compose.demo.yml` descarga.
- Detalle interno: [c4-contenedores.md](c4-contenedores.md). Decisiones: [ADR-001](../adr/ADR-001-arquitectura-hexagonal-y-api-first.md), [ADR-003](../adr/ADR-003-ci-cd.md), [ADR-006](../adr/ADR-006-autenticacion-jwt.md).

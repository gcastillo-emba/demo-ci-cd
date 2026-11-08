# Resumen ejecutivo de la bitácora de fallas

> Generado en modo headless con `claude -p` (`scripts/claude-resumen-bitacora.mjs`) el 2026-11-08 · modelo: claude-opus-5[1m] · costo informado: USD 0.9315.
> Fuente: [`docs/bitacora-fallas.md`](bitacora-fallas.md). Texto generado por un modelo: revisarlo antes de citarlo.

## Panorama

La bitácora registra **26 fallas** entre el **7 y el 8 de noviembre de 2026**, distribuidas en las fases F0, F1, F3 y la capacidad C1 (F2 no registró ninguna). Más de la mitad (13 de 26) son problemas de herramienta o entorno —versiones de CLI, plugins Maven, Windows, Docker— y no defectos de producto. Ninguna falla implicó desactivar o relajar un gate: todas se cerraron con corrección, y dos quedaron como desviación o seguimiento explícito (filas 10 y 23).

## Fallas por fase y por tipo

| Fase                      | Cantidad | Tipos (cantidad)                                                                                                                      |
| ------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| F0 (fundaciones)          | 6        | Herramienta o entorno (6)                                                                                                             |
| F1 (capa de agentes y CI) | 5        | Gate mecánico (2) · Gate de juicio (2) · Herramienta o entorno (1)                                                                    |
| F3 (walking skeleton)     | 12       | Herramienta o entorno (5) · Gate mecánico (4) · Gate de juicio (1) · Concurrencia entre worktrees (1) · Test inestable (1)            |
| C1 (clientes y asuntos)   | 3        | Instrucción del orquestador (1) · Herramienta o entorno (1) · Concurrencia entre worktrees (1)                                        |
| **Total**                 | **26**   | **Herramienta o entorno 13 · Gate mecánico 6 · Gate de juicio 3 · Concurrencia 2 · Instrucción del orquestador 1 · Test inestable 1** |

Cada falla se cuenta una sola vez por su causa dominante. La fila 7 combina un gate mecánico en rojo (`format`, `hooks`) con hallazgos adversariales de la revisión F1; las filas 17, 24 y 25 se manifestaron como gate `--e2e` en rojo, pero 25 se clasifica por su causa real (concurrencia).

## Causas recurrentes

- **Windows y rutas con espacios** (`Sistemas IA`): glob obligatorio en `node --test` (fila 4), `URISyntaxException` del generador OpenAPI (fila 12) y `mvnw.cmd` no resuelto por `NoDefaultCurrentDirectoryInExePath=1` (filas 16 y 18).
- **Diferencias Windows ↔ Linux no detectadas por los gates locales**: el mismo generador produce modelos distintos por SO (fila 17) y el checksum del wrapper Maven falla solo dentro del build Docker por falta de `unzip` (fila 24).
- **Versiones y subcomandos de herramientas verificados tarde**: palabras clave `SHALL` de OpenSpec 1.12 (fila 3), `openspec new change` en vez de `openspec change` (fila 5), google-java-format 1.37.0 incompatible con Spotless (fila 13), springdoc y Swagger UI (fila 15), jsdom 30 sin `HTMLDialogElement` (fila 21).
- **Gates silenciosos o permisivos**: ArchUnit reportando 0 tests y dando verde a una regla que debía fallar (fila 14) y `merge-si-verde.mjs` con fecha controlada por el autor y degradación fail-open (fila 9).
- **Hallazgos de seguridad detectados por gate mecánico o de juicio, nunca descartados**: CodeQL `js/regex-injection` (fila 11), CSRF global en `SecurityConfig` (fila 19), acciones de GitHub fijadas por tag mayor (fila 10) y la revisión ASVS v4 L1 con 1 Media y 7 Bajas (fila 23).
- **Trabajo en paralelo sin aislamiento**: tres worktrees comparten nombre de proyecto compose, etiqueta de imagen y puerto 8080, lo que recreó contenedores ajenos y sobrescribió imágenes (filas 22 y 25).
- **Precondiciones de plataforma y secretos no resueltas en F0**: plan de GitHub y visibilidad del repo (fila 1), `ANTHROPIC_API_KEY` ausente (fila 2) y subagentes creados a mitad de sesión que Claude Code no registra (fila 8).

## Top 5 lecciones para MADA-MB

1. **Probar todo gate nuevo con un caso que debe fallar antes de confiar en su verde.** Un conteo de 0 tests es una falla, no un éxito (fila 14); los scripts de control de merge deben fallar cerrado y usar marcas de tiempo del servidor, nunca datos del autor (fila 9).
2. **Correr `npm run gate -- --e2e` —el build Docker, única prueba en Linux— antes de abrir el PR.** Los gates locales en Windows no cubren Linux ni los checksums del wrapper en cada entorno que lo ejecuta (filas 17 y 24).
3. **Aislar cada worktree con `COMPOSE_PROJECT_NAME`, etiqueta de imagen y puertos propios, o serializar el uso de Docker; nunca reutilizar un stack sin `--build`.** `scripts/e2e.mjs` debería abortar si el proyecto compose ya está en uso (filas 22 y 25).
4. **Cerrar en F0 las precondiciones de plataforma y las versiones de herramientas.** Verificar plan y visibilidad de GitHub, provisionar secretos con modo degradado explícito, confirmar subcomandos reales de cada CLI, fijar versiones de formateadores y probar los scripts npm en Windows desde el primer commit (filas 1–5, 8, 13, 16).
5. **Preferir código que no dispare alertas y acotar al mínimo toda excepción de seguridad, documentándola junto al código.** Descartar una alerta es un gate de juicio humano; el rate limiting del login y la medida anti-enumeración deben estar en la plantilla de autenticación del skeleton desde el primer commit (filas 11, 19, 23).

# Resumen ejecutivo de la bitácora de fallas

> Generado en modo headless con `claude -p` (`scripts/claude-resumen-bitacora.mjs`) el 2026-11-08 · modelo: claude-opus-5[1m] · costo informado: USD 1.3553.
> Fuente: [`docs/bitacora-fallas.md`](bitacora-fallas.md). Texto generado por un modelo: revisarlo antes de citarlo.

## Panorama

La bitácora registra **26 fallas** entre el 7 y el 8 de noviembre de 2026, distribuidas en las fases F0, F1, F3 y C1 del POC "Asuntos & Horas". La mayor concentración ocurre en F3 (walking skeleton), con 11 entradas, seguida de F0 y F1 con 6 cada una. Predominan los fallos de herramienta o entorno, en particular los derivados de ejecutar en Windows lo que luego corre en Linux y contenedores.

## Fallas por fase y por tipo

| Fase      | Cantidad | Tipos presentes                                                                                                                                                          |
| --------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| F0        | 6        | Herramienta o entorno (#1, #2, #3, #4, #5, #6)                                                                                                                           |
| F1        | 6        | Gate mecánico (#7, #11); gate de juicio (#9); herramienta o entorno (#8, #10)                                                                                            |
| F3        | 11       | Gate mecánico (#17, #19, #24, #25, #26); gate de juicio (#23); herramienta o entorno (#12, #13, #15, #16, #18); concurrencia entre worktrees (#25); test inestable (#26) |
| C1        | 3        | Instrucción del orquestador (#20); herramienta o entorno (#21); concurrencia entre worktrees (#22)                                                                       |
| **Total** | **26**   | —                                                                                                                                                                        |

Notas sobre la clasificación: la fila #14 (ArchUnit reportando 0 tests) se cuenta en F3 como gate mecánico silencioso; #17, #25 y #26 aparecen bajo más de un tipo porque el gate mecánico falló por causa de entorno, concurrencia o inestabilidad del test, respectivamente.

## Causas recurrentes

- **Diferencias entre Windows y Linux/contenedor.** El mismo comando pasa en local y falla en Docker o CI: rutas con espacios (#12), `NoDefaultCurrentDirectoryInExePath` (#16, #18), globs de `node --test` (#4), generación distinta de modelos por SO (#17) y el checksum del wrapper Maven según exista `unzip` (#24).
- **Concurrencia entre worktrees sobre recursos compartidos de Docker.** Mismo `COMPOSE_PROJECT_NAME` y misma etiqueta de imagen `:local` provocan recreación de contenedores e imágenes sobrescritas (#22, #25).
- **Gates que fallan abiertos o silenciosos.** `merge-si-verde` comparaba etiquetas con fecha controlada por el autor y continuaba ante datos faltantes (#9); ArchUnit reportaba 0 tests y daba verde sin evaluar reglas (#14); los guards solo modelaban escrituras y no lecturas ni `gh api` (#7).
- **Supuestos sobre herramientas sin verificar su versión o subcomandos reales.** OpenSpec 1.12 (#3, #5), google-java-format 1.37.0 con Spotless (#13), springdoc 2.x y Swagger UI (#15), jsdom 30 sin `HTMLDialogElement` (#21).
- **Alertas de seguridad de CodeQL como bloqueo de merge.** Dos ocurrencias que exigieron reescribir código en lugar de descartar la alerta (#11, #19), más los hallazgos ASVS del gate de juicio sobre la plantilla de autenticación (#23).
- **Precondiciones de plataforma y secretos no resueltas en F0.** Plan de GitHub y visibilidad del repo (#1) y ausencia de `ANTHROPIC_API_KEY` (#2) condicionaron el diseño de los gates de seguridad y agénticos.
- **Instrucciones del orquestador parafraseadas en vez de citar la fuente de verdad OpenSpec** (#20).
- **Manipulación de código con escapes a través del shell**, que corrompió regex de los hooks (#6).

## Top 5 lecciones para MADA-MB

1. **Correr el gate `--e2e` (build Docker, Linux) antes de abrir cada PR**, no solo los gates locales de Windows: es la primera prueba real del entorno de CI y ya detectó divergencias de generador y de wrapper (#17, #24).
2. **Aislar cada worktree con su propio `COMPOSE_PROJECT_NAME`, etiqueta de imagen y puertos, o serializar explícitamente el uso de Docker**, y hacer que `scripts/e2e.mjs` aborte si el proyecto compose ya está en uso (#22, #25).
3. **Probar todo gate nuevo con un caso que debe fallar y verificar el conteo de tests ejecutados**; un gate que reporta cero casos o degrada de forma permisiva se trata como falla, no como verde, y los scripts de control de merge fallan cerrado usando marcas de tiempo del servidor (#9, #14).
4. **Cerrar las precondiciones de plataforma en F0**: plan y visibilidad del repositorio, secretos provisionados, versiones y subcomandos reales de cada CLI, y acciones de GitHub fijadas por SHA desde el primer workflow, registrando cualquier desviación con fecha de cierre (#1, #2, #3, #5, #10).
5. **Incorporar a la plantilla del skeleton las defensas de autenticación desde el primer commit** —rate limiting del login, medida anti-enumeración, tests negativos del token— y preferir reescribir el código antes que descartar una alerta de seguridad, acotando y documentando cada excepción (#11, #19, #23).

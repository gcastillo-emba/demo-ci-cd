## Why

Desde OpenSpec 1.14.1, `openspec validate --strict` rechaza los requisitos cuyo texto supera 500 caracteres («Requirement text is very long (>500 characters)»). Las cuatro specs vigentes tienen 17 requisitos así, por lo que el PR #23 fijó OpenSpec en 1.14.0 y `.github/dependabot.yml` ignora `>= 1.14.1` (bitácora, fila 44). El gate `spec` no se relaja: para poder subir el validador hay que acortar o partir esos requisitos.

## What Changes

- **15 requisitos se acortan en su lugar** (`MODIFIED`), conservando nombre y todos sus escenarios: los ejemplos y casos borde pasan a los escenarios que ya los verifican, se quita texto que repite otro requisito o un escenario, o se condensa la redacción sin cambiar su significado.
  - `clients-matters`: 7 requisitos, más "Validación de datos del asunto", que pasa a decir explícitamente que el máximo de 200 caracteres del título se mide tras recortar espacios (ese dato salía de "El Socio crea asuntos").
  - `description-assistant`: 3 requisitos.
  - `hours-dashboard`: 2 requisitos.
  - `time-entries`: 3 requisitos.
- **2 requisitos se parten** (`REMOVED` + `ADDED`) en requisitos de un solo comportamiento, con sus escenarios movidos sin cambios:
  - `description-assistant` / "Criterios de aceptación de evals" (744 caracteres) → "Casos de eval del asistente" y "Cobertura de los casos de eval".
  - `time-entries` / "Listado de registros con filtros y paginación" (1097 caracteres) → "Filtros y paginación del listado de registros", "Alcance del listado según el rol", "Orden del listado de registros", "Fecha límite de edición en el listado" y "Validación de parámetros del listado".
- Ningún escenario se agrega ni se elimina, y ninguno cambia de nombre. Cinco escenarios reciben el detalle que salió del texto del requisito (ver `design.md`, D2).
- Se sube `@fission-ai/openspec` de 1.14.0 a 1.15.0 y se quita su `ignore` de `.github/dependabot.yml`.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `clients-matters`: redacción de requisitos, sin cambio de comportamiento.
- `description-assistant`: redacción de requisitos y un requisito partido, sin cambio de comportamiento.
- `hours-dashboard`: redacción de requisitos, sin cambio de comportamiento.
- `time-entries`: redacción de requisitos y un requisito partido, sin cambio de comportamiento.

## Impact

- **Specs**: `openspec/specs/*/spec.md` (al archivar el cambio). Los requisitos de la sección `ADDED` quedan al final de cada spec, como hace `openspec archive`.
- **Código, contrato y tests**: sin cambios. Los tests se trazan por nombre de escenario y ningún nombre cambia. Los requisitos que el código cita por nombre en comentarios (`ClientsPage.tsx`, `MattersPage.tsx`, `MatterDetailPage.tsx`, `AssistantNoteTest.java`, `AssistantPromptTest.java`) conservan su nombre.
- **Evidencia histórica**: `docs/evidence/add-time-entries/trazabilidad.md` cita "Listado de registros con filtros y paginación"; se deja como está porque documenta el cambio `add-time-entries` tal como se archivó.
- **Herramientas**: `package.json` / `package-lock.json` (OpenSpec 1.15.0) y `.github/dependabot.yml`.

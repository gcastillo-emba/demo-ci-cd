# AGENTS.md — Contrato de agentes (POC "Asuntos & Horas")

Contrato neutral para **cualquier** agente (Claude Code, Codex, Copilot u otro) que trabaje en este repo.
La fuente de verdad de las decisiones es [`docs/plan-maestro.md`](docs/plan-maestro.md). Glosario: [`docs/glosario.md`](docs/glosario.md).

- Documentación e interfaz de usuario: **español**. Código, identificadores, commits y nombres de rama: **inglés**.
- Glosario mínimo: Cliente = `Client`, Asunto = `Matter`, Registro de horas = `TimeEntry`, Abogado = `Lawyer`, Socio = `Partner`. Roles: `PARTNER` (SOCIO) y `LAWYER` (ABOGADO).
- Datos **100 % sintéticos**. Nunca datos reales de clientes, abogados ni asuntos.

## 1. Metodología MADA-MB

Cada cambio recorre este ciclo. No se salta ninguna etapa.

| Etapa         | Qué se hace                                                                                                       | Salida verificable                                                       |
| ------------- | ----------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| **Research**  | Leer plan maestro, specs vigentes (`openspec/specs/`), código y ADRs relacionados.                                | Lista de archivos y reglas afectadas.                                    |
| **Plan**      | Redactar o actualizar el cambio OpenSpec (`proposal.md`, specs delta, `design.md` con **Supuestos**, `tasks.md`). | `npm run spec:validate` en verde y cambio aprobado.                      |
| **Implement** | Implementar solo lo que dice `tasks.md`, dentro de las rutas del rol.                                             | Diff acotado a las rutas propias.                                        |
| **Verificar** | Gates mecánicos: `npm run gate` (o `-- --e2e`), CI `ci-ok`.                                                       | Gates en verde, evidencia en `docs/evidence/<cambio>/`.                  |
| **Revisar**   | Gates de juicio: `revisor` y `seguridad`, independientes del autor.                                               | Etiquetas `revisor:aprobado` y `seguridad:ok` después del último commit. |
| **Merge**     | Solo vía `scripts/merge-si-verde.mjs` (squash, `--match-head-commit`).                                            | PR fusionado; cambio OpenSpec archivado.                                 |

### Regla spec-driven

**Nada se implementa sin un cambio OpenSpec aprobado.** Si una tarea no está en `tasks.md`, primero se actualiza el cambio (y vuelve a validarse); después se implementa. Los requisitos se escriben como `El sistema SHALL … (DEBE)` / `SHALL NOT … (NO DEBE)` y los escenarios como Dado / Cuando / Entonces.

### Gates mecánicos vs gates de juicio

- **Mecánicos**: los decide una máquina (validación OpenSpec, lint de contrato, compilación contra interfaces generadas, tests, ArchUnit, JaCoCo ≥ 80 %, Prettier/Spotless, E2E, gitleaks, CodeQL). Un gate mecánico **nunca** se desactiva ni se relaja.
- **De juicio**: los decide un agente o humano independiente (`revisor`, `seguridad`). Quedan registrados como comentario y etiqueta en el PR.

### Supuestos explícitos

Toda decisión no cubierta por la spec se anota en la sección **Supuestos** de `design.md` del cambio, y se reporta en la salida del agente.

## 2. Roles y rutas propias

Cada agente **solo escribe** en sus rutas. `scripts/check-ownership.mjs` lo verifica de forma mecánica.

| Rol              | Rutas que posee (escritura)                                                            | Notas                                                                     |
| ---------------- | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `arquitecto`     | `docs/adr/**`, `docs/arquitectura/**`, `openspec/changes/*/design.md`                  | Decisiones y ADRs; no escribe código.                                     |
| `redactor-specs` | `openspec/**`, `api/openapi.yaml`                                                      | Único dueño del contrato OpenAPI y de las specs.                          |
| `dev-backend`    | `backend/**`                                                                           | Spring Boot hexagonal.                                                    |
| `dev-frontend`   | `frontend/**` **excepto** `frontend/src/api/generated/**`                              | El cliente generado se regenera con `npm run api:generate`, nunca a mano. |
| `qa`             | `**/test/**`, `**/*.test.*`, `e2e/**`, `evals/**`, `docs/evidence/**`                  | Tests, E2E, evals y evidencia.                                            |
| `seguridad`      | — (solo lectura)                                                                       | Comenta en el PR y pone `seguridad:ok`.                                   |
| `devops`         | `.github/**`, `docker-compose*.yml`, `**/Dockerfile`, `scripts/**`, `.claude/hooks/**` | CI/CD, contenedores, scripts y hooks.                                     |
| `revisor`        | — (solo lectura)                                                                       | **Nunca revisa trabajo propio.** Comenta y pone `revisor:aprobado`.       |

Archivos transversales (`AGENTS.md`, `CLAUDE.md`, `README.md`, `docs/bitacora-fallas.md`, `package.json` raíz) los edita el orquestador; cualquier agente puede **agregar** filas a la bitácora.

## 3. Definición de terminado (por cambio OpenSpec)

Un cambio está terminado solo si se cumple todo:

1. Cambio OpenSpec aprobado y `npm run spec:validate` en verde.
2. Todas las tareas de `tasks.md` marcadas `[x]`.
3. Cada escenario de la spec tiene al menos un test que lo cubre (trazable por nombre o comentario).
4. `npm run gate -- --e2e` en verde en local y `ci-ok` en verde en el PR.
5. JaCoCo ≥ 80 % en `domain` + `application`; sin bajar la cobertura previa.
6. Contrato y cliente generado sincronizados (`npm run api:check`).
7. Evidencia visual en `docs/evidence/<cambio>/` enlazada en el PR.
8. Supuestos nuevos anotados en `design.md`; documentación y glosario actualizados.
9. Etiquetas `revisor:aprobado` y `seguridad:ok` posteriores al último commit.
10. Fallas del ciclo registradas en `docs/bitacora-fallas.md`.
11. Cambio archivado (`openspec archive`) en el mismo PR.

## 4. Formato de salida obligatorio

Todo agente termina su trabajo con este bloque:

```markdown
## Resumen

<1–3 frases: qué se hizo y en qué estado queda>

## Archivos

- <ruta> — <creado|modificado|eliminado>

## Gates ejecutados

| Gate | Comando | Resultado |
| ---- | ------- | --------- |

## Supuestos nuevos

- <supuesto> (anotado en <ruta>) | Ninguno

## Fallas → bitácora

- <# de fila agregada en docs/bitacora-fallas.md> | Ninguna
```

## 5. Reglas

- **Nunca** commitear secretos, `.env*` (salvo `.env.example`), llaves ni tokens.
- **Nunca** desactivar, saltar ni relajar un gate (`--no-verify`, `@Disabled`, `skip`, umbrales más bajos, `continue-on-error`).
- **Nunca** bajar la cobertura.
- **Solo datos sintéticos.**
- **Nunca** `git push --force`, push a `main`, push de tags ni `gh pr merge` fuera de `scripts/merge-si-verde.mjs`.
- Commits y títulos de PR en **Conventional Commits** con scopes `c1|c2|c3|c4|api|spec|ci|docs|deps|skeleton|release` (los mismos que valida `.github/workflows/pr-title.yml`).
- **Un PR por cambio OpenSpec** (excepción documentada: F2 agrupa las 4 propuestas).
- No editar archivos generados (`frontend/src/api/generated/**`, `.claude/skills/openspec-*`, `.claude/commands/opsx/*`).
- Todo spawn desde scripts Node pasa por `scripts/lib/run.mjs`.

## 6. Reglas de parada

El agente se detiene y pide intervención humana cuando:

1. Una acción implica **dinero o recursos de nube** pagos.
2. Falta un **secreto** o credencial.
3. La acción es **irreversible y externa** (push de tag, release, cambio de visibilidad, borrado remoto).
4. El **mismo gate falla 3 veces** seguidas (se registra en la bitácora antes de parar).
5. La spec es ambigua o contradictoria y no se puede resolver con un supuesto razonable.
6. Se necesita escribir fuera de las rutas propias del rol.

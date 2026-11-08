# Runbook de despliegue de demo

Cómo levantar, verificar, actualizar y bajar la demo de **Asuntos & Horas** con las imágenes publicadas en GHCR, sin compilar nada. Este runbook se adjunta a cada release de GitHub junto con `docker-compose.demo.yml` (ver `release.yml`).

> **Solo demo.** Datos 100 % sintéticos y secretos de demo públicos. No exponer este stack a internet (ver [Notas de seguridad](#notas-de-seguridad)).

## Índice

1. [Requisitos](#requisitos)
2. [Ruta rápida (imágenes publicadas)](#ruta-rápida-imágenes-publicadas)
3. [Verificación](#verificación)
4. [Operación: logs, parada, reinicio y actualización](#operación-logs-parada-reinicio-y-actualización)
5. [Cómo llegan las imágenes a GHCR](#cómo-llegan-las-imágenes-a-ghcr)
6. [Flujo de release](#flujo-de-release)
7. [Rollback](#rollback)
8. [Solución de problemas](#solución-de-problemas)
9. [Notas de seguridad](#notas-de-seguridad)
10. [Recorrido por capacidad](#recorrido-por-capacidad)
11. [Anexo A: verificación cronometrada (`demo:check`)](#anexo-a-verificación-cronometrada-democheck)
12. [Anexo B: resumen headless de la bitácora](#anexo-b-resumen-headless-de-la-bitácora)

## Requisitos

| Requisito                   | Detalle                                                                                                                          |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| Docker Engine o Desktop     | Con soporte `linux/amd64` (las imágenes se publican solo para amd64; en Apple Silicon corren con emulación).                     |
| Docker Compose v2           | `docker compose version` debe responder (el plugin v2; el binario antiguo `docker-compose` v1 no sirve: se usa `--wait`).        |
| Puerto libre                | `8080` en el host (configurable con `FRONTEND_PORT`).                                                                            |
| Red                         | Acceso a `ghcr.io` y Docker Hub (`postgres:17-alpine`). Los paquetes de GHCR son públicos: **no hace falta `docker login`**.     |
| Memoria                     | ~1,5 GB libres para los tres contenedores (PostgreSQL, backend Spring Boot y nginx).                                             |
| Archivo de compose          | Solo `docker-compose.demo.yml` (de la raíz del repo o adjunto en la release). No hace falta clonar el repo ni tener Node o Java. |
| Opcional: `gh` (GitHub CLI) | Para verificar las attestations de procedencia de las imágenes.                                                                  |

## Ruta rápida (imágenes publicadas)

Desde la carpeta donde está `docker-compose.demo.yml`:

```bash
docker compose -f docker-compose.demo.yml up -d --wait
```

- Descarga `ghcr.io/gcastillo-emba/demo-ci-cd-backend` y `demo-ci-cd-frontend` con la etiqueta `latest` (la última versión verde de `main`) y `postgres:17-alpine`.
- `--wait` vuelve solo cuando los tres servicios están `healthy`: la cadena de healthchecks es postgres (`pg_isready`) → backend (readiness de Actuator) → frontend (`/healthz` de nginx).
- El proyecto compose se llama `asuntos-horas-demo` (contenedores `asuntos-horas-demo-*`), distinto del stack de desarrollo `asuntos-horas`. Ambos usan el puerto 8080, así que no pueden correr a la vez.
- El backend arranca con el perfil `demo`: migraciones Flyway + seed sintético (usuarios y datos de cada capacidad).

Para una versión concreta (una release):

```bash
TAG=v0.1.0 docker compose -f docker-compose.demo.yml up -d --wait
```

En PowerShell: `$env:TAG='v0.1.0'; docker compose -f docker-compose.demo.yml up -d --wait` (y `Remove-Item Env:TAG` después).

Luego abrir **http://localhost:8080**.

### Tiempo medido

Medido el 2026-11-08 con `npm run demo:check` (Windows 11, Docker Desktop (Engine 29.6, Compose 5.3), imágenes `latest` de `main` en `320040b`):

| Escenario                                    | `pull` | `up -d --wait` | Total (pull + up) | Hasta login OK |
| -------------------------------------------- | -----: | -------------: | ----------------: | -------------: |
| En frío (imágenes de la app sin caché local) | 21,7 s |         65,8 s |        **87,5 s** |         88,4 s |
| En caliente (imágenes ya descargadas)        |  5,4 s |         41,1 s |        **46,6 s** |         47,3 s |

Holgadamente bajo la meta de **5 minutos** del plan maestro. La mayor parte del `up` es el arranque de Spring Boot más las migraciones Flyway (`start_period` de 30 s en el healthcheck del backend). En la medición en frío `postgres:17-alpine` ya estaba en caché; en una máquina sin ninguna imagen suma unos segundos de descarga.

## Verificación

1. **Estado de los contenedores**: todos `healthy`.

   ```bash
   docker compose -f docker-compose.demo.yml ps
   ```

2. **Readiness a través de nginx** (mismo origen que usa el navegador). La ruta `/api/` de nginx hace proxy al backend y Spring Security deja públicos `/api/actuator/health/**` e `/info`:

   ```bash
   curl -s http://localhost:8080/api/actuator/health/readiness
   # {"status":"UP"}
   ```

   En PowerShell usar `curl.exe` (no el alias `curl` de `Invoke-WebRequest`) o `Invoke-RestMethod http://localhost:8080/api/actuator/health/readiness`. El healthcheck de nginx por sí solo es `http://localhost:8080/healthz` (no depende del backend).

3. **Login por API** (opcional):

   ```bash
   curl -s -X POST http://localhost:8080/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"socia@asuntos.test","password":"Demo2026!"}'
   # {"accessToken":"…","tokenType":"Bearer","expiresIn":7200,"user":{…,"role":"PARTNER"}}
   ```

4. **Login en la interfaz**: abrir http://localhost:8080 e ingresar con un usuario de demo. Todos son ficticios y comparten la contraseña `Demo2026!`:

   | Usuario                 | Nombre       | Rol                |
   | ----------------------- | ------------ | ------------------ |
   | `socia@asuntos.test`    | Socia Demo   | `PARTNER` (SOCIO)  |
   | `abogado1@asuntos.test` | Abogado Uno  | `LAWYER` (ABOGADO) |
   | `abogado2@asuntos.test` | Abogada Dos  | `LAWYER` (ABOGADO) |
   | `abogado3@asuntos.test` | Abogado Tres | `LAWYER` (ABOGADO) |

5. **Contrato**: el contrato es `api/openapi.yaml` (en el repo). nginx solo hace proxy de `/api/`, así que Swagger UI del backend no es accesible en la demo; en modo desarrollo (`npm run dev`) está en http://localhost:8081/swagger-ui.html.

Todo lo anterior (salvo la interfaz) lo automatiza `npm run demo:check` ([Anexo A](#anexo-a-verificación-cronometrada-democheck)).

## Operación: logs, parada, reinicio y actualización

| Acción                                     | Comando                                                             |
| ------------------------------------------ | ------------------------------------------------------------------- |
| Logs de todos los servicios (en vivo)      | `docker compose -f docker-compose.demo.yml logs -f`                 |
| Logs de un servicio (últimas 200 líneas)   | `docker compose -f docker-compose.demo.yml logs --tail 200 backend` |
| Detener sin borrar datos                   | `docker compose -f docker-compose.demo.yml stop`                    |
| Volver a arrancar                          | `docker compose -f docker-compose.demo.yml up -d --wait`            |
| **Bajar y borrar datos** (reinicio limpio) | `docker compose -f docker-compose.demo.yml down -v`                 |
| Actualizar a la última imagen de `main`    | `docker compose -f docker-compose.demo.yml up -d --wait` (ver nota) |

- Los logs del backend son ECS estructurados (JSON); el MDC solo lleva `userId` y `requestId`, nunca PII.
- `down -v` borra el volumen `asuntos-horas-demo_pgdata`: al volver a subir se recrea la base y el seed con fechas frescas.
- **Actualización**: ambos servicios de la app declaran `pull_policy: always`, así que cada `up` consulta GHCR y recrea los contenedores si la etiqueta apunta a un digest nuevo. Para descargar sin reiniciar: `docker compose -f docker-compose.demo.yml pull`. Si cambió el esquema o el seed, conviene `down -v` antes del `up`.
- Limpiar imágenes viejas: `docker image prune` (solo las colgantes) o `docker rmi ghcr.io/gcastillo-emba/demo-ci-cd-backend:<tag>`.

## Cómo llegan las imágenes a GHCR

Lo decide [ADR-003](adr/ADR-003-ci-cd.md); lo ejecuta `.github/workflows/cd.yml`.

1. Un PR se fusiona en `main` (squash, solo vía `scripts/merge-si-verde.mjs`).
2. `ci.yml` corre sobre el push a `main`. Si termina en verde, dispara `cd.yml` (`workflow_run`, solo `conclusion == success` y evento `push`).
3. `cd.yml` construye `backend/Dockerfile` y `frontend/Dockerfile` (contexto = raíz del repo, `linux/amd64`, caché `gha`) **desde el commit probado** (`head_sha`) y publica:

   | Imagen                                       | Etiquetas                       |
   | -------------------------------------------- | ------------------------------- |
   | `ghcr.io/gcastillo-emba/demo-ci-cd-backend`  | `sha-<short>`, `main`, `latest` |
   | `ghcr.io/gcastillo-emba/demo-ci-cd-frontend` | `sha-<short>`, `main`, `latest` |

   `<short>` son los 7 primeros caracteres del SHA del commit de `main`. Las imágenes llevan las etiquetas OCI `org.opencontainers.image.source` y `.revision`.

4. Cada imagen recibe una **attestation de procedencia** (SLSA build provenance, `actions/attest-build-provenance`) subida al registro. Para verificarla:

   ```bash
   gh attestation verify oci://ghcr.io/gcastillo-emba/demo-ci-cd-backend:latest --owner gcastillo-emba
   gh attestation verify oci://ghcr.io/gcastillo-emba/demo-ci-cd-frontend:latest --owner gcastillo-emba
   ```

   Ver qué commit hay detrás de una etiqueta:

   ```bash
   docker buildx imagetools inspect ghcr.io/gcastillo-emba/demo-ci-cd-backend:latest
   docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' ghcr.io/gcastillo-emba/demo-ci-cd-backend:latest
   ```

## Flujo de release

Una release **no recompila**: re-etiqueta las imágenes `sha-<short>` que CD ya publicó y probó, con el mismo digest.

1. Verificar que el commit a liberar está en `main` y que CD publicó sus imágenes (`sha-<short>` existe en GHCR).
2. **El humano** crea y empuja el tag (el hook `guard-git-push` bloquea los pushes de tags del agente; AGENTS.md § 6, regla 3):

   ```bash
   git switch main && git pull --ff-only
   git tag -a v0.1.0 -m "v0.1.0"
   git push origin v0.1.0
   ```

3. `release.yml` (disparado por `v*`):
   - verifica que el commit del tag es ancestro de `origin/main`;
   - re-etiqueta `sha-<short>` → `vX.Y.Z` y `latest-stable` en ambas imágenes (`docker buildx imagetools create`);
   - genera las notas con **git-cliff** (`cliff.toml`, secciones en español, a partir de los Conventional Commits desde el tag anterior);
   - crea la GitHub Release `vX.Y.Z` con `gh release create --verify-tag`, adjuntando `docker-compose.demo.yml` y este runbook.
4. Probar la release: `TAG=v0.1.0 docker compose -f docker-compose.demo.yml up -d --wait`.

Etiquetas disponibles en cada imagen: `latest` y `main` (punta verde de `main`), `sha-<short>` (cada commit de `main`), `vX.Y.Z` (cada release) y `latest-stable` (la última release).

## Rollback

La demo no tiene estado que preservar, así que volver atrás es cambiar la etiqueta:

```bash
docker compose -f docker-compose.demo.yml down -v
TAG=v0.1.0 docker compose -f docker-compose.demo.yml up -d --wait   # release anterior
# o un commit concreto de main:
TAG=sha-320040b docker compose -f docker-compose.demo.yml up -d --wait
```

- `down -v` es necesario si la versión nueva aplicó migraciones Flyway que la anterior no conoce (Flyway rechaza una base con migraciones "del futuro").
- Para mantener `latest-stable` como etiqueta fija de la demo: `TAG=latest-stable`.
- Si una imagen publicada está rota en `main`, el arreglo es un PR nuevo (CD vuelve a publicar `latest`); nunca se borra ni se reescribe una etiqueta `vX.Y.Z` a mano.

## Solución de problemas

| Síntoma                                                               | Causa probable                                                                                               | Qué hacer                                                                                                                                                                                                                                                              |
| --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Bind for 0.0.0.0:8080 failed: port is already allocated`             | Otro proceso (o el stack de desarrollo `asuntos-horas`) usa el 8080.                                         | Ver quién: `docker ps --filter publish=8080`; en Windows `netstat -ano \| findstr :8080`; en Linux/macOS `lsof -i :8080`. Bajar el otro stack (`docker compose down`) o usar otro puerto: `FRONTEND_PORT=8081 docker compose -f docker-compose.demo.yml up -d --wait`. |
| Login responde **429** "Demasiados intentos"                          | Rate limiting de nginx: 5 intentos por minuto por IP, con ráfaga de 5 (ADR-006).                             | Esperar 1 minuto (cabecera `Retry-After: 60`). En una demo con varias personas detrás de la misma IP, ingresar por turnos.                                                                                                                                             |
| Fechas del seed "viejas" (registros de hace semanas, tableros vacíos) | El volumen `pgdata` sobrevivió de una corrida anterior; el seed usa fechas relativas al momento de cargarse. | `docker compose -f docker-compose.demo.yml down -v` y volver a subir.                                                                                                                                                                                                  |
| `--wait` falla con `backend ... unhealthy`                            | Spring Boot no llegó a readiness (base inaccesible, migración fallida, poca memoria).                        | `docker compose -f docker-compose.demo.yml logs --tail 200 backend`. Si hay error de Flyway por migraciones de otra versión: `down -v`. Revisar la memoria asignada a Docker Desktop.                                                                                  |
| `dependency postgres failed to start` / contenedores recreados solos  | Otro `docker compose up` del mismo proyecto en paralelo (bitácora #22 y #25).                                | No correr dos veces la demo a la vez; `npm run demo:check` y `npm run e2e` se serializan con el candado `compose-e2e`.                                                                                                                                                 |
| `manifest unknown` al hacer pull                                      | La etiqueta `TAG` no existe (release no creada o `sha-` mal escrito).                                        | `docker buildx imagetools inspect ghcr.io/gcastillo-emba/demo-ci-cd-backend:<tag>`; revisar las releases y el run de CD.                                                                                                                                               |
| `denied` / `unauthorized` al hacer pull                               | Credenciales viejas de `ghcr.io` en Docker.                                                                  | `docker logout ghcr.io` (los paquetes son públicos).                                                                                                                                                                                                                   |
| `no matching manifest for linux/arm64`                                | Máquina ARM sin emulación.                                                                                   | Activar la emulación amd64 de Docker Desktop (Rosetta/QEMU) o `DOCKER_DEFAULT_PLATFORM=linux/amd64`.                                                                                                                                                                   |

### Notas para Windows

- Usar Docker Desktop con backend WSL 2; asignarle al menos 4 GB de memoria (Settings → Resources) si también corre el stack de desarrollo o Testcontainers.
- PowerShell no acepta `VAR=valor comando`: usar `$env:TAG='v0.1.0'; docker compose …` o `cmd /c "set TAG=v0.1.0&& docker compose …"`.
- `curl` en PowerShell 5.1 es un alias de `Invoke-WebRequest`: usar `curl.exe` o `Invoke-RestMethod`. Para el login por API en PowerShell:

  ```powershell
  Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
    -ContentType 'application/json' `
    -Body '{"email":"socia@asuntos.test","password":"Demo2026!"}'
  ```

- Si la ruta del repo tiene espacios (como `Sistemas IA`), citarla siempre: `docker compose -f "C:\ruta con espacios\docker-compose.demo.yml" …`.
- Un antivirus o la VPN corporativa pueden bloquear `ghcr.io`: si el pull se cuelga, probar `docker pull ghcr.io/gcastillo-emba/demo-ci-cd-frontend:latest` a mano.

## Notas de seguridad

- **Secretos solo de demo.** El secreto JWT por defecto vive en un único lugar, `application-demo.yml` (perfil `demo`), y es público y sin valor real (ADR-006). La contraseña de PostgreSQL (`asuntos-dev`) y la de los usuarios (`Demo2026!`) también son públicas. Para cambiar el secreto JWT, exportar `APP_JWT_SECRET` (≥ 32 bytes) antes del `up`; compose solo lo pasa si está definido.
- **Datos 100 % sintéticos.** Ningún cliente, abogado ni asunto es real. No cargar datos reales en esta demo.
- **No exponer a internet.** El stack no tiene TLS, usa secretos conocidos y el rate limiting asume una sola capa de proxy. Publica solo el puerto del frontend (`8080`, en todas las interfaces del host): en redes compartidas, limitarlo a loopback con `FRONTEND_PORT=127.0.0.1:8080` o un firewall. PostgreSQL y el backend no publican puertos.
- **Procedencia verificable.** Antes de una demo ante terceros, verificar las attestations con `gh attestation verify` ([ver arriba](#cómo-llegan-las-imágenes-a-ghcr)).
- Más contexto: [ADR-005](adr/ADR-005-repo-publico-y-modo-de-seguridad.md) (repo público y modo de seguridad) y [ADR-006](adr/ADR-006-autenticacion-jwt.md) (autenticación JWT).

## Recorrido por capacidad

Guion sugerido para mostrar cada capacidad en la demo. Las secciones marcadas **(pendiente)** se completan cuando la capacidad se fusione en `main` y sus imágenes lleguen a GHCR.

| Capacidad                       | Cambio OpenSpec             | Estado en `latest` |
| ------------------------------- | --------------------------- | ------------------ |
| C1 — Clientes y asuntos         | `add-clients-matters`       | Disponible         |
| C2 — Registro de horas          | `add-time-entries`          | En construcción    |
| C3 — Tablero de horas           | `add-hours-dashboard`       | Pendiente          |
| C4 — Asistente de descripciones | `add-description-assistant` | Pendiente          |

### C1 — Clientes y asuntos

1. Ingresar como `socia@asuntos.test` (SOCIO): ver el listado de clientes sembrados, buscar, crear un cliente y abrirle un asunto.
2. Ingresar como `abogado1@asuntos.test` (ABOGADO): listar y consultar clientes y asuntos; las altas y el cierre de asuntos son exclusivos del SOCIO (la API responde `403`).
3. Evidencia de referencia: `docs/evidence/add-clients-matters/`.

### C2 — Registro de horas (pendiente)

_Se completa al fusionar `feat(c2)`: registrar horas como ABOGADO, límite diario de 24 h, ventana de edición de 7 días, aprobar y rechazar con motivo como SOCIO._

### C3 — Tablero de horas (pendiente)

_Se completa al fusionar `feat(c3)`: tablero de los últimos 30 días con alcance por rol._

### C4 — Asistente de descripciones (pendiente)

_Se completa al fusionar `feat(c4)`: sugerencias del asistente con el adaptador `fake` (flag `app.features.description-assistant`, activo en el perfil `demo`); aceptar, editar o descartar._

## Anexo A: verificación cronometrada (`demo:check`)

`scripts/demo-check.mjs` (desde el repo clonado, con Node 22) automatiza la ruta rápida y mide los tiempos:

```bash
npm run demo:check                                   # latest
npm run demo:check -- --tag v0.1.0                   # una release
npm run demo:check -- --keep                         # deja el stack arriba al terminar
npm run demo:check -- --metricas .mada/metricas-f5.txt   # agrega la medición al archivo
```

Pasos: toma el candado de máquina `compose-e2e` (el mismo de `npm run e2e`, porque ambos usan el 8080) → `docker compose -f docker-compose.demo.yml pull` → `up -d --wait` → readiness vía nginx → `POST /api/auth/login` con `socia@asuntos.test` → imprime `pull_s`, `up_wait_s`, `pull_mas_up_s` y `hasta_login_ok_s` → `down -v` y libera el candado. Si algo falla, imprime los últimos logs de cada servicio y termina con código distinto de 0.

## Anexo B: resumen headless de la bitácora

Ejemplo de Claude Code en **modo headless** (capacidad 11 del plan maestro): `scripts/claude-resumen-bitacora.mjs` ejecuta

```bash
claude -p "<prompt>" --output-format json --max-turns 3 --allowedTools Read
```

y escribe un resumen ejecutivo en español de `docs/bitacora-fallas.md` (fallas por fase y tipo, causas recurrentes y las 5 lecciones principales para MADA-MB) en [`docs/bitacora-resumen.md`](bitacora-resumen.md), con una cabecera que indica que se generó en modo headless, la fecha, el modelo y el costo informado.

| Uso                 | Comando                                                                                                                               |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| Local               | `npm run bitacora:resumen` (requiere el CLI `claude` autenticado; si no está instalado, termina con código 0 y un aviso)              |
| Ver el comando      | `npm run bitacora:resumen -- --dry-run`                                                                                               |
| Otra ruta de salida | `npm run bitacora:resumen -- --salida /tmp/resumen.md`                                                                                |
| CI (manual)         | Actions → **Resumen de la bitácora (headless)** → _Run workflow_. Sube el resumen como artefacto `bitacora-resumen`; no hace commits. |

- El agente solo puede **leer** (`--allowedTools Read`) y tiene como máximo 3 turnos.
- El workflow `.github/workflows/bitacora-resumen.yml` es solo `workflow_dispatch`, con `permissions: contents: read`, Claude Code fijado a una versión exacta y un guard que deja el job **inerte** (aviso `::notice::`) mientras no exista el secreto `ANTHROPIC_API_KEY` (bitácora #2).
- Primera corrida local (2026-11-08): 67 s, 2 turnos, costo informado USD 0,93.

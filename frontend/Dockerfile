# syntax=docker/dockerfile:1.7
# Imagen del frontend (Vite + React + TS) servida por nginx sin privilegios en el puerto 8080.
# El contexto de build es la RAÍZ del repo: npm workspaces necesita el package.json y el
# package-lock.json raíz. Uso:
#   docker build -f frontend/Dockerfile -t asuntos-horas-frontend .

FROM node:22-alpine AS build
WORKDIR /workspace
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    npm_config_fund=false \
    npm_config_audit=false

# 1) Manifiestos: la capa de node_modules se reutiliza mientras no cambien.
#    e2e/package.json se copia solo para que el lockfile de workspaces sea consistente;
#    sus dependencias no se instalan.
COPY package.json package-lock.json ./
COPY frontend/package.json frontend/package.json
COPY e2e/package.json e2e/package.json
RUN npm ci --workspace frontend --include-workspace-root=false

# 2) Fuentes (incluye el cliente generado en frontend/src/api/generated, versionado).
COPY frontend frontend
RUN npm run build -w frontend

FROM nginxinc/nginx-unprivileged:1.31-alpine AS runtime
COPY --chown=nginx:nginx frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build --chown=nginx:nginx /workspace/frontend/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 \
  CMD wget -q -O /dev/null http://localhost:8080/healthz || exit 1

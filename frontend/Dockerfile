FROM node:22-alpine AS build

WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend ./
RUN npm run build

FROM caddy:2.10-alpine

WORKDIR /srv
COPY frontend/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist ./

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${PORT:-80}/health || exit 1


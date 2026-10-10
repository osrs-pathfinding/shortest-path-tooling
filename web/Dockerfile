# Build context: the repository root, for the contracts in corpus/. From web/: docker build -f Dockerfile ..
FROM node:24-alpine AS build
WORKDIR /app/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY corpus /app/corpus
COPY web/index.html web/tsconfig*.json web/vite.config.ts ./
COPY web/scripts scripts
COPY web/src src
RUN npm run build

FROM caddy:2.10-alpine
COPY web/deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/web/dist /srv

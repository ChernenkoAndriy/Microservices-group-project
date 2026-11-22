# Build: docker build -t spotty-frontend:latest frontend
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# The generated API types are committed (src/api/generated), so the contracts are not needed here.
RUN npm run build

FROM nginx:1.27-alpine
# BACKEND_URL: where /api is proxied (the API gateway Service in the cluster). Substituted into the config at start.
ENV BACKEND_URL=http://api-gateway:8080
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1

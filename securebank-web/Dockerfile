# syntax=docker/dockerfile:1
FROM node:26.10-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27.5-alpine
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="securebank-web"       org.opencontainers.image.description="SecureBank Internet Banking (SPA servida pelo nginx)"       org.opencontainers.image.version="${VERSION}"       org.opencontainers.image.revision="${REVISION}"       org.opencontainers.image.source="https://github.com/Alexandregv1080p/SecureBank"
USER root
RUN apk upgrade --no-cache
USER 101
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080

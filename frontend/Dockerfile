ARG NODE_BUILD_IMAGE=node:20-alpine
ARG NGINX_RUNTIME_IMAGE=nginx:1.27-alpine

FROM ${NODE_BUILD_IMAGE} AS build

ARG NPM_REGISTRY
ARG ALPINE_APK_MIRROR

WORKDIR /app

RUN if [ -n "${ALPINE_APK_MIRROR}" ]; then \
      sed -i "s|https\?://dl-cdn.alpinelinux.org/alpine|${ALPINE_APK_MIRROR}|g" /etc/apk/repositories; \
    fi \
    && if [ -n "${NPM_REGISTRY}" ]; then \
      npm config set registry "${NPM_REGISTRY}"; \
    fi

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM ${NGINX_RUNTIME_IMAGE}

ARG ALPINE_APK_MIRROR

RUN if [ -n "${ALPINE_APK_MIRROR}" ]; then \
      sed -i "s|https\?://dl-cdn.alpinelinux.org/alpine|${ALPINE_APK_MIRROR}|g" /etc/apk/repositories; \
    fi \
    && apk add --no-cache apache2-utils

COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY docker-entrypoint.d/30-cvect-auth.envsh /docker-entrypoint.d/30-cvect-auth.envsh
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

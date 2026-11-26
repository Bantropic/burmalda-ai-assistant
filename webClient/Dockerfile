FROM ubuntu:24.04 AS build

ARG FLUTTER_VERSION=3.47.4
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl git unzip xz-utils zip libglu1-mesa \
    && rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --branch "${FLUTTER_VERSION}" \
    https://github.com/flutter/flutter.git /opt/flutter
ENV PATH="/opt/flutter/bin:${PATH}"
RUN flutter config --no-analytics --enable-web && flutter precache --web

WORKDIR /app
COPY pubspec.yaml pubspec.lock ./
RUN flutter pub get
COPY . .
ARG API_BASE_URL=
RUN flutter build web --release --no-pub --dart-define=API_BASE_URL=${API_BASE_URL}

FROM nginx:stable-alpine AS runtime
ENV BACKEND_HOST=backend BACKEND_PORT=8080 NGINX_ENVSUBST_FILTER=^BACKEND_
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/build/web /usr/share/nginx/html
EXPOSE 80

ARG MAVEN_BUILD_IMAGE=maven:3.9.9-eclipse-temurin-17
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy

FROM ${MAVEN_BUILD_IMAGE} AS build

ARG MAVEN_MIRROR_URL

WORKDIR /build

RUN if [ -n "${MAVEN_MIRROR_URL}" ]; then \
      mkdir -p /root/.m2 && \
      printf '%s\n' \
      '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' \
      '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' \
      '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' \
      '  <mirrors>' \
      '    <mirror>' \
      '      <id>cvect-build-mirror</id>' \
      '      <name>CVect Build Mirror</name>' \
      "      <url>${MAVEN_MIRROR_URL}</url>" \
      '      <mirrorOf>*</mirrorOf>' \
      '    </mirror>' \
      '  </mirrors>' \
      '</settings>' > /root/.m2/settings.xml; \
    fi

COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -DskipTests package

FROM ${JAVA_RUNTIME_IMAGE}

ARG UBUNTU_APT_MIRROR

WORKDIR /app

RUN if [ -n "${UBUNTU_APT_MIRROR}" ] && [ -f /etc/apt/sources.list ]; then \
      sed -i "s|http://archive.ubuntu.com/ubuntu/|${UBUNTU_APT_MIRROR}/|g; s|http://security.ubuntu.com/ubuntu/|${UBUNTU_APT_MIRROR}/|g; s|https://archive.ubuntu.com/ubuntu/|${UBUNTU_APT_MIRROR}/|g; s|https://security.ubuntu.com/ubuntu/|${UBUNTU_APT_MIRROR}/|g" /etc/apt/sources.list; \
    fi \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/*.jar /app/app.jar

RUN groupadd --system cvect \
    && useradd --system --gid cvect --create-home --home-dir /home/cvect --shell /usr/sbin/nologin cvect \
    && mkdir -p /data/storage /app \
    && chown -R cvect:cvect /app /data/storage /home/cvect

EXPOSE 8080

ENV CVECT_UPLOAD_STORAGE_DIR=/data/storage

USER cvect

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar /app/app.jar"]

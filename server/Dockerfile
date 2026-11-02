# syntax=docker/dockerfile:1
#
# La imagen de la API, en dos etapas: se compila con el JDK y se ejecuta con
# el JRE, así que ni Gradle, ni el código fuente, ni la caché de dependencias
# llegan a la imagen que corre.
#
# Las bases van fijadas por tag y digest. El tag dice qué se quiso; el digest,
# qué se probó. Sin el digest, la misma línea construye otra imagen el día que
# alguien vuelva a publicar el tag, y Dependabot (ecosistema docker, en
# /server) mantiene los dos al día en un PR que se puede revisar.

# El build corre en la arquitectura de quien construye, nunca emulado: el jar
# es bytecode y es el mismo para amd64 que para arm64. Compilar bajo QEMU para
# Ampere tardaría diez veces más para producir exactamente el mismo archivo.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk@sha256:83f4a7fc27df94dcc7b8de04d14cbbba8da14406f921e966969c3b09000aa507 AS build
WORKDIR /src

# Primero solo lo que decide las dependencias, para que la capa que las baja
# no se invalide con cada cambio de código.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY domain/build.gradle.kts domain/
COPY app/build.gradle.kts app/
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew --no-daemon --quiet :app:dependencies > /dev/null

COPY domain/src domain/src
COPY app/src app/src
# Sin tests: los corre el trabajo `test` de CI contra un Postgres de verdad,
# y aquí no hay Docker con el que levantarlo.
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew --no-daemon --quiet :app:bootJar

FROM eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0

# Un usuario propio, sin shell ni casa, con un UID fijo y alto: fijo para que
# el compose y quien audite sepan cuál es, y alto para que no coincida con
# ningún usuario de la máquina que monta la imagen.
RUN groupadd --system --gid 10001 finance \
    && useradd --system --uid 10001 --gid finance --no-create-home \
       --home-dir /nonexistent --shell /usr/sbin/nologin finance

WORKDIR /app
# El jar es de root y solo se lee: el proceso no puede reescribir su propio
# código aunque alguien lo convenza de intentarlo.
COPY --from=build --chown=root:root --chmod=0444 /src/app/build/libs/finance-api.jar finance-api.jar

USER 10001:10001
EXPOSE 8080

# ExitOnOutOfMemoryError: un proceso que se quedó sin memoria queda en un
# estado que nadie sabe describir; mejor morir y que `restart` lo levante.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/finance-api.jar"]

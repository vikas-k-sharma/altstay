# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage. Uses the repo's own Maven wrapper rather than a maven: base
# image, so the build here resolves exactly the Maven version pinned in
# .mvn/wrapper/maven-wrapper.properties — the same one `mvnw clean verify`
# uses locally.
#
# Tests are NOT run here. The unit suite needs no credentials, but the image
# build has no Docker-side access to ALTSTAY_DB_* and running a partial suite
# would make a green image build look like a green test run. CI runs the suite
# in its own job; see .github/workflows/deploy.yml.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Wrapper + POM first, so the dependency download layer is cached until the
# POM actually changes. Without this split every source edit re-downloads
# Spring Boot 4, Spring AI and the GenAI SDK.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package \
 && cp target/*.jar /build/app.jar

# ---------------------------------------------------------------------------
# Runtime stage. JRE only — the JDK's compiler and tooling are ~180MB of
# attack surface this container has no use for.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Never run as root. ACA does not require it, which is exactly why it is easy
# to forget.
RUN groupadd --system --gid 1001 altstay \
 && useradd --system --uid 1001 --gid altstay altstay

COPY --from=build --chown=altstay:altstay /build/app.jar app.jar

USER altstay
EXPOSE 8080

# MaxRAMPercentage, not -Xmx: the container's memory limit is set by the ACA
# revision, so the heap should follow it rather than be pinned to a number
# here that silently disagrees.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

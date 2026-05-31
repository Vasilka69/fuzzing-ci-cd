FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY . .
RUN ./mvnw -pl services/vcs-service -am -DskipTests package

FROM eclipse-temurin:21-jre AS runtime

RUN apt-get update \
    && apt-get install --no-install-recommends -y git \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/services/vcs-service/target/vcs-service-*.jar /app/app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

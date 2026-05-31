FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY . .
RUN ./mvnw -pl demo/mock-master-publisher -am -DskipTests package

FROM eclipse-temurin:21-jre AS runtime

RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/demo/mock-master-publisher/target/mock-master-publisher-*.jar /app/app.jar

USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

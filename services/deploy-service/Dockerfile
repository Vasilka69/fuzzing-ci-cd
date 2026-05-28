FROM eclipse-temurin:17-jre-alpine

ARG JAR_FILE=services/deploy-service/target/*.jar
COPY ${JAR_FILE} /opt/app/app.jar

USER 10001
ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]

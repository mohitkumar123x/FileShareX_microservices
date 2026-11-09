FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY notification-service/build/libs/*SNAPSHOT.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]

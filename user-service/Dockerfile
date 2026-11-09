FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY user-service/build/libs/*SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

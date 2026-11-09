FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY eureka-server/build/libs/*SNAPSHOT.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]

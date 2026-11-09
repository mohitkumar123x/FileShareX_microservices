FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY config-server/build/libs/*SNAPSHOT.jar app.jar
COPY config-repo /config-repo
EXPOSE 9999
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jdk-alpine AS builder
RUN apk add --no-cache ca-certificates
WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle/ ./gradle/
COPY notification-service/ ./notification-service/
RUN chmod +x gradlew && ./gradlew :notification-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/notification-service/build/libs/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]

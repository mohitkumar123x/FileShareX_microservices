FROM eclipse-temurin:21-jdk-alpine AS builder
RUN apk add --no-cache ca-certificates
WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle/ ./gradle/
COPY gateway/ ./gateway/
RUN chmod +x gradlew && ./gradlew :gateway:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/gateway/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

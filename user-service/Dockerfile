FROM eclipse-temurin:21-jdk-alpine AS builder
RUN apk add --no-cache ca-certificates
WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle/ ./gradle/
COPY user-service/ ./user-service/
RUN chmod +x gradlew && ./gradlew :user-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/user-service/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

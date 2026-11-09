FROM eclipse-temurin:21-jdk-alpine AS builder
RUN apk add --no-cache ca-certificates
WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle/ ./gradle/
COPY eureka-server/ ./eureka-server/
RUN chmod +x gradlew && ./gradlew :eureka-server:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/eureka-server/build/libs/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]

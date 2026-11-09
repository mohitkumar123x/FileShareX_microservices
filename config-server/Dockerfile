FROM eclipse-temurin:21-jdk-alpine AS builder
RUN apk add --no-cache ca-certificates
WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle/ ./gradle/
COPY config-server/ ./config-server/
COPY config-repo/ ./config-repo/
RUN ./gradlew :config-server:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/config-server/build/libs/*.jar app.jar
COPY --from=builder /app/config-repo /config-repo
EXPOSE 9999
ENTRYPOINT ["java", "-jar", "app.jar"]

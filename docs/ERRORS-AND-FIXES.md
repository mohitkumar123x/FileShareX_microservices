# Errors, Issues & Fixes - Microservices Architecture

This document catalogs all known errors, issues, and their fixes encountered during the microservices conversion.

---

## Table of Contents

1. [Gateway Authentication Issues](#1-gateway-authentication-issues)
2. [Service-to-Service Communication Issues](#2-service-to-service-communication-issues)
3. [MongoDB Connection Issues](#3-mongodb-connection-issues)
4. [Cloudflare R2 Storage Issues](#4-cloudflare-r2-storage-issues)
5. [Circuit Breaker Issues](#5-circuit-breaker-issues)
6. [Service Discovery Issues](#6-service-discovery-issues)
7. [Configuration Issues](#7-configuration-issues)
8. [Docker Compose Issues](#8-docker-compose-issues)
9. [Build & Dependency Issues](#9-build--dependency-issues)
10. [Logging & Monitoring Issues](#10-logging--monitoring-issues)
11. [Docker Build Issues](#11-docker-build-issues)
12. [Local Service Startup Issues](#12-local-service-startup-issues)
13. [Prometheus & Grafana Monitoring Issues](#13-prometheus--grafana-monitoring-issues)
14. [MongoDB to PostgreSQL Migration](#14-mongodb-to-postgresql-migration)
15. [CI/CD & Cloud Deployment Issues](#15-cicd--cloud-deployment-issues)

---

## 1. Gateway Authentication Issues

### Issue 1.1: SecurityContext Not Propagated to Downstream Services

**Error:**
```
org.springframework.security.authentication.BadCredentialsException: Authentication object cannot be null
```

**Root Cause:** Spring Cloud Gateway uses WebFlux (reactive) which doesn't propagate SecurityContext to downstream services. Controllers using `SecurityContextHolder.getContext().getAuthentication()` receive null.

**Fix:**
1. Changed `ClerkJwtAuthFilter` from `WebFilter` to `GlobalFilter`
2. Extract `clerkId` from JWT claims
3. Pass as `X-Clerk-Id` header to downstream services
4. Updated all controllers to read from header instead of SecurityContextHolder

**Files Changed:**
- `gateway/src/main/java/.../security/ClerkJwtAuthFilter.java`
- `gateway/src/main/java/.../config/SecurityConfig.java`
- `file-service/src/main/java/.../controller/FileController.java`
- `user-service/src/main/java/.../controller/UserCreditsController.java`
- `payment-service/src/main/java/.../controller/PaymentController.java`
- `payment-service/src/main/java/.../controller/TransactionController.java`

**Code Change:**
```java
// Before (broken)
String clerkId = SecurityContextHolder.getContext().getAuthentication().getName();

// After (fixed)
@RequestHeader("X-Clerk-Id") String clerkId
```

---

### Issue 1.2: Gateway Route Configuration Not Working

**Error:**
```
404 Not Found when accessing services through Gateway
```

**Root Cause:** Routes configured with incorrect path predicates or missing `StripPrefix` filter.

**Fix:**
```yaml
# Correct route configuration
spring.cloud.gateway.routes[0].id: user-service
spring.cloud.gateway.routes[0].uri: lb://user-service
spring.cloud.gateway.routes[0].predicates[0]: Path=/api/v1.0/users/**,/api/v1.0/webhooks/**
spring.cloud.gateway.routes[0].filters[0]: StripPrefix=2
```

**Key Points:**
- Use `lb://` prefix for load-balanced routes
- `StripPrefix=2` removes `/api/v1.0` prefix before forwarding
- Path predicates must match incoming request paths

---

## 2. Service-to-Service Communication Issues

### Issue 2.1: RestClient Not Available in Downstream Services

**Error:**
```
No bean named 'restClientBuilder' available
```

**Root Cause:** Spring Boot 3.x requires explicit `RestClient.Builder` bean configuration.

**Fix:**
```java
@Component
public class UserClient {
    private final RestClient restClient;

    public UserClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }
}
```

---

### Issue 2.2: Circuit Breaker Not Working

**Error:**
```
Resilience4j circuit breaker not opening on failures
```

**Root Cause:** Missing `@CircuitBreaker` annotation or incorrect fallback method signature.

**Fix:**
```java
@CircuitBreaker(name = "userService", fallbackMethod = "creditsFallback")
public int getCredits(String clerkId) {
    // actual implementation
}

// Fallback must have same return type + Exception parameter
public int creditsFallback(String clerkId, Exception e) {
    log.warn("Fallback triggered: {}", e.getMessage());
    throw new RuntimeException("Service unavailable");
}
```

**Configuration:**
```yaml
resilience4j.circuitbreaker.instances.userService.failure-rate-threshold: 50
resilience4j.circuitbreaker.instances.userService.wait-duration-in-open-state: 30s
resilience4j.circuitbreaker.instances.userService.sliding-window-size: 10
```

---

## 3. MongoDB Connection Issues

### Issue 3.1: Multiple Services Connecting to Same Database

**Error:**
```
Collection naming conflicts between services
```

**Root Cause:** All services sharing same MongoDB instance with default database name.

**Fix:**
- Each service uses separate database name
- Docker Compose runs separate MongoDB instances

```yaml
# user-service
spring.data.mongodb.uri: mongodb://mongo-users:27017/filesharex_users

# file-service
spring.data.mongodb.uri: mongodb://mongo-files:27017/filesharex_files

# payment-service
spring.data.mongodb.uri: mongodb://mongo-payments:27017/filesharex_payments

# notification-service
spring.data.mongodb.uri: mongodb://mongo-notifications:27017/filesharex_notifications
```

---

### Issue 3.2: MongoDB Index Conflicts

**Error:**
```
com.mongodb.MongoBulkWriteException: E11000 duplicate key error collection
```

**Root Cause:** Unique index on `clerkId` in profiles collection conflicts when multiple services try to create profiles.

**Fix:**
- Only User Service manages profile creation
- Other services call User Service internal API for profile operations

---

## 4. Cloudflare R2 Storage Issues

### Issue 4.1: S3 SDK Region Configuration

**Error:**
```
software.amazon.awssdk.core.exception.SdkClientException: Unable to execute HTTP request
```

**Root Cause:** R2 requires `region: "auto"` and specific endpoint format.

**Fix:**
```java
s3Client = S3Client.builder()
    .endpointOverride(URI.create("https://ACCOUNT_ID.r2.cloudflarestorage.com"))
    .credentialsProvider(StaticCredentialsProvider.create(credentials))
    .region(Region.of("auto"))  // Required for R2
    .forcePathStyle(true)       // Required for R2
    .build();
```

---

### Issue 4.2: File Key Format Issues

**Error:**
```
NoSuchKey: The specified key does not exist
```

**Root Cause:** File key format mismatch between upload and download.

**Fix:**
```java
// Consistent key format
private String buildKey(MultipartFile file, String clerkId) {
    String extension = "";
    if (originalFilename != null && originalFilename.contains(".")) {
        extension = originalFilename.substring(originalFilename.lastIndexOf("."));
    }
    return String.format("uploads/%s/%s%s", clerkId, UUID.randomUUID(), extension);
}
```

---

## 5. Circuit Breaker Issues

### Issue 5.1: Fallback Method Not Found

**Error:**
```
java.lang.NoSuchMethodException: creditsFallback(String, Throwable)
```

**Root Cause:** Fallback method signature doesn't match the original method.

**Fix:**
```java
// Original method
@CircuitBreaker(name = "userService", fallbackMethod = "creditsFallback")
public int getCredits(String clerkId) { ... }

// Fallback must have: same return type + same params + Exception
public int creditsFallback(String clerkId, Exception e) { ... }
```

---

## 6. Service Discovery Issues

### Issue 6.1: Services Not Registering with Eureka

**Error:**
```
com.netflix.discovery.DiscoveryClient: No instances available for service-name
```

**Root Cause:** Missing Eureka client configuration or Eureka server not running.

**Fix:**
```yaml
# In each service's application.yml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

**Dependencies:**
```groovy
implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
```

---

### Issue 6.2: Eureka Server Startup Order

**Error:**
```
Services failing to start because Eureka is not ready
```

**Root Cause:** Services starting before Eureka server is available.

**Fix:**
```yaml
# Docker Compose healthcheck
eureka-server:
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3

# Services depend on healthy Eureka
gateway:
  depends_on:
    eureka-server:
      condition: service_healthy
```

---

## 7. Configuration Issues

### Issue 7.1: Config Server Not Loading Properties

**Error:**
```
Could not locate PropertySource: 404 Not Found
```

**Root Cause:** Config Server not finding configuration files.

**Fix:**
```yaml
# config-server application.yml
spring:
  cloud:
    config:
      server:
        native:
          search-locations: file:./config-repo
        default-label: main
```

---

### Issue 7.2: Service Not Fetching Config from Server

**Error:**
```
Using default config for service-name
```

**Root Cause:** Service not configured to use Config Server.

**Fix:**
```yaml
# In each service's bootstrap.yml or application.yml
spring:
  cloud:
    config:
      enabled: true
      uri: http://localhost:9999
  config:
    import: optional:configserver:http://localhost:9999
```

---

## 8. Docker Compose Issues

### Issue 8.1: Services Cannot Resolve Hostnames

**Error:**
```
java.net.UnknownHostException: mongo-users
```

**Root Cause:** Services using `localhost` instead of Docker service names.

**Fix:**
```yaml
# Use Docker service names, not localhost
environment:
  MONGO_URI: mongodb://mongo-users:27017/filesharex_users
  USER_SERVICE_URL: http://user-service:8081/api/v1.0
```

---

### Issue 8.2: Port Conflicts

**Error:**
```
Bind for 0.0.0.0:27017 failed: port is already allocated
```

**Root Cause:** Multiple MongoDB instances trying to use same host port.

**Fix:**
```yaml
# Map to different host ports
mongo-users:
  ports: ["27017:27017"]
mongo-files:
  ports: ["27018:27017"]
mongo-payments:
  ports: ["27019:27017"]
mongo-notifications:
  ports: ["27020:27017"]
```

---

## 9. Build & Dependency Issues

### Issue 9.1: Spring Cloud Gateway Conflicts with Spring MVC

**Error:**
```
Parameter 0 of method in required a bean of type 'ReactiveWebServerFactory'
```

**Root Cause:** Gateway uses WebFlux, other services use MVC. Mixing dependencies causes conflicts.

**Fix:**
- Gateway: Use `spring-boot-starter-webflux` (not `web`)
- Other services: Use `spring-boot-starter-web` (not `webflux`)
- Never mix both in same service

---

### Issue 9.2: Lombok Not Working in Submodules

**Error:**
```
cannot find symbol: @Slf4j, @RequiredArgsConstructor, etc.
```

**Root Cause:** Lombok dependencies not properly configured in parent build.gradle.

**Fix:**
```groovy
// Parent build.gradle
subprojects {
    dependencies {
        compileOnly 'org.projectlombok:lombok'
        annotationProcessor 'org.projectlombok:lombok'
        testCompileOnly 'org.projectlombok:lombok'
        testAnnotationProcessor 'org.projectlombok:lombok'
    }
}
```

---

## 10. Logging & Monitoring Issues

### Issue 10.1: Trace ID Not Propagating Across Services

**Error:**
```
Logs from different services not correlatable
```

**Root Cause:** Micrometer tracing not configured properly.

**Fix:**
```groovy
// In each service's build.gradle
implementation 'io.micrometer:micrometer-tracing-bridge-brave'
```

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

---

### Issue 10.2: Prometheus Not Scraping Metrics

**Error:**
```
No data points in Grafana dashboard
```

**Root Cause:** Prometheus scrape targets misconfigured or actuator endpoints not exposed.

**Fix:**
```yaml
# Each service must expose prometheus endpoint
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

# Prometheus config
scrape_configs:
  - job_name: 'filesharex-user-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['user-service:8081']
```

---

## Quick Reference: Common Commands

### Start Services
```bash
# Build all services
./gradlew clean build

# Start with Docker Compose
docker-compose up -d

# Check service health
curl http://localhost:8080/actuator/health
```

### Debug Commands
```bash
# Check Eureka registrations
curl http://localhost:8761/eureka/apps

# Check Config Server
curl http://localhost:9999/gateway/default

# View logs
docker logs -f filesharex-gateway
docker logs -f filesharex-user-service
```

### Reset Commands
```bash
# Stop all services
docker-compose down

# Remove volumes (fresh start)
docker-compose down -v

# Rebuild specific service
docker-compose build --no-cache user-service
```

---

## Known Limitations

1. **No Service Mesh:** Inter-service communication uses REST; consider gRPC for high-performance internal calls
2. **No Message Queue:** Synchronous communication only; consider RabbitMQ/Kafka for async events
3. **No Distributed Transactions:** Saga pattern not implemented; eventual consistency assumed
4. **No API Rate Limiting:** Gateway does not implement rate limiting; add if needed
5. **No Centralized Logging:** Using local files only; consider ELK stack or Loki for production

---

## 11. Docker Build Issues

### Issue 11.1: Gradle Download Timeout Inside Docker Containers

**Error:**
```
Exception in thread "main" java.io.IOException: Downloading from https://services.gradle.org/distributions/gradle-8.9-bin.zip failed: timeout (10000ms)
Caused by: java.net.SocketTimeoutException: Read timed out
ERROR: process "/bin/sh -c ./gradlew :gateway:bootJar --no-daemon" did not complete successfully: exit code: 1
```

**Root Cause:** Docker containers have limited network access during multi-stage builds. Gradle wrapper tries to download the distribution inside the container but the network connection times out.

**Fix:** Build JARs locally first, then use Docker only for running:
```bash
# Build locally
./gradlew clean bootJar --no-daemon

# Then run with Docker or locally
docker-compose up -d
```

Alternatively, pre-cache the Gradle distribution in a Docker volume or use a base image with Gradle pre-installed.

---

## 12. Local Service Startup Issues

### Issue 12.1: Path with Spaces Causes "Invalid or corrupt jarfile"

**Error:**
```
Error: Invalid or corrupt jarfile C:\Users\mohit\Downloads\SWITCH\DEV\PROJECTS\FILE SHARE\FileShareX\...
```

**Root Cause:** `Start-Process` with `-ArgumentList` splits arguments on spaces. A path containing spaces (e.g., `FILE SHARE`) gets truncated, making the JVM see an incomplete jar path.

**Fix:** Wrap the jar path in escaped quotes:
```powershell
# Broken
Start-Process java -ArgumentList "-jar", "$root\eureka-server\build\libs\eureka-server-1.0.0-SNAPSHOT.jar"

# Fixed
Start-Process -FilePath "java" -ArgumentList "-jar","`"$root\eureka-server\build\libs\eureka-server-1.0.0-SNAPSHOT.jar`""
```

---

### Issue 12.2: Gateway Health Check Shows DOWN Despite Being Running

**Error:**
```
[DOWN] Gateway (port 8080)
```

**Root Cause:** Gateway uses Spring Cloud Gateway (WebFlux + Netty), not Tomcat. It doesn't expose `/actuator/health` by default on the same path, and the health endpoint may not be accessible without authentication.

**Fix:** Check the correct endpoint or use an alternative URL:
```powershell
# Instead of /actuator/health, check if the process is running
Invoke-WebRequest -Uri "http://localhost:8080/actuator/prometheus" -UseBasicParsing -TimeoutSec 5

# Or check that Netty is listening
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

---

### Issue 12.3: Notification Service Health Check Shows DOWN (Mail Health Degraded)

**Error:**
```
[DOWN] Notification Service (port 8084)
WARN: Mail health check failed - jakarta.mail.AuthenticationFailedException: failed to connect, no password specified?
```

**Root Cause:** The `spring-boot-starter-actuator` mail health indicator tries to connect to the SMTP server at startup. No mail credentials are configured, so the health check reports DOWN.

**Fix:** This is expected in development. The service is running and fully functional - only the mail health indicator is degraded. To suppress this:
```yaml
# In notification-service application.yml
management:
  health:
    mail:
      enabled: false
```

---

## 13. Prometheus & Grafana Monitoring Issues

### Issue 13.1: All Prometheus Targets DOWN - Docker Service Names Don't Resolve

**Error:**
```
[DOWN] gateway -> http://gateway:8080/actuator/prometheus
[DOWN] user-service -> http://user-service:8081/actuator/prometheus
```

**Root Cause:** `prometheus.yml` uses Docker service names (`gateway:8080`) as targets. When services run on the host (not in containers), Prometheus running in Docker cannot resolve these hostnames.

**Fix:** Use `host.docker.internal` which resolves to the host machine from inside Docker:
```yaml
# Before (broken for host-mode services)
targets: ['gateway:8080']

# Fixed
targets: ['host.docker.internal:8080']
```

---

### Issue 13.2: Prometheus Targets DOWN - Missing Context Path in Scrape URL

**Error:**
```
[DOWN] user-service - received unsupported Content-Type "application/json" and no fallback_scrape_protocol specified
```

**Root Cause:** User, file, payment, and notification services have `server.servlet.context-path: /api/v1.0`. Prometheus was scraping `/actuator/prometheus` but the actual endpoint is at `/api/v1.0/actuator/prometheus`.

**Fix:** Set `metrics_path` per job to include the context path:
```yaml
- job_name: 'filesharex-user-service'
  metrics_path: '/api/v1.0/actuator/prometheus'
  static_configs:
    - targets: ['host.docker.internal:8081']
```

---

### Issue 13.3: Eureka Server Not Exposing Prometheus Metrics

**Error:**
```
[DOWN] eureka-server - 404 Not Found on /actuator/prometheus
```

**Root Cause:** Two issues:
1. `micrometer-registry-prometheus` dependency missing from `eureka-server/build.gradle`
2. `management.endpoints.web.exposure.include` only had `health,info` (no `prometheus`)

**Fix:**
```groovy
// eureka-server/build.gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'  // Added
}
```

```yaml
# eureka-server application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus  # Added metrics,prometheus
```

---

### Issue 13.4: Gateway Blocks Actuator Endpoints with 401 Unauthorized

**Error:**
```
[DOWN] gateway - The remote server returned an error: (401) Unauthorized
```

**Root Cause:** `SecurityConfig.java` only permits `/webhooks/**`, `/files/public/**`, `/files/download/**`, `/health`. The `/actuator/**` path is not in the `permitAll()` list, so Clerk JWT auth filter rejects unauthenticated requests to actuator.

**Fix:** Add `/actuator/**` to the permitted paths in `SecurityConfig.java`:
```java
// Before
.pathMatchers("/webhooks/**", "/files/public/**", "/files/download/**", "/health").permitAll()

// After
.pathMatchers("/webhooks/**", "/files/public/**", "/files/download/**", "/health", "/actuator/**").permitAll()
```

---

### Issue 13.5: Config Server Returns JSON Instead of Prometheus Metrics

**Error:**
```
[DOWN] config-server - received unsupported Content-Type "application/json"
```

**Root Cause:** Spring Cloud Config Server intercepts `/actuator/{profile}` requests, treating "prometheus" as a Spring profile name. It returns the config for that profile (JSON) instead of Prometheus metrics (text/plain).

**Fix:** Exclude config-server from Prometheus scraping. It's an infrastructure service with minimal metrics value and this is a known Spring Cloud Config Server limitation:
```yaml
# prometheus.yml - removed config-server job
# config-server excluded: Spring Cloud Config Server intercepts /actuator/prometheus as a profile lookup
```

---

### Issue 13.6: Grafana Dashboard Shows "No Data" Despite Prometheus Targets Being UP

**Error:**
```
All Grafana dashboard panels show "No data"
Prometheus targets show all services as UP
```

**Root Cause:** Grafana datasource configured with `http://prometheus:9090` (Docker service name). Docker's embedded DNS doesn't resolve container names on networks created separately via `docker network create`. The Grafana container cannot reach Prometheus.

**Fix:** Use `host.docker.internal` instead of the Docker service name in the datasource config:
```yaml
# Before (broken)
url: http://prometheus:9090

# Fixed
url: http://host.docker.internal:9090
```

After updating, recreate the Grafana container:
```bash
docker rm -f filesharex-grafana
docker run -d --name filesharex-grafana --network filesharex-monitoring -p 3000:3000 \
  -e GF_SECURITY_ADMIN_USER=admin -e GF_SECURITY_ADMIN_PASSWORD=admin \
  -v "./monitoring/grafana/provisioning:/etc/grafana/provisioning" \
  -v "./monitoring/grafana/dashboards:/var/lib/grafana/dashboards" \
  grafana/grafana:latest
```

---

## 14. MongoDB to PostgreSQL Migration

**Scope:** The persistence layer of the four business services (`user-service`, `file-service`, `payment-service`, `notification-service`) was moved from MongoDB (Spring Data MongoDB) to PostgreSQL 17 (Spring Data JPA / Hibernate), database-per-service.

**Important:** The project was **not started** during this pass (per instruction "do not run the project"). Every item below was identified by static review of the sources, Gradle build outputs and Docker configuration. The rebuild/verification commands in 14.12 are still pending.

**How to read this section:** the service sources, Gradle files, `config-repo/` and `docker-compose.yml` were already converted to JPA/PostgreSQL in the working tree when this pass started — they were re-verified line by line here. This pass itself updated the `HLD/` documents (14.11), the stale `mongo_data` entries in `.gitignore`/`.dockerignore` (14.9) and this document.

### Issue 14.1: Stale Boot JARs and Build Resources Still Contain MongoDB Config

**Symptom (found on disk):**
```
user-service/build/libs/user-service-1.0.0-SNAPSHOT.jar          2026-09-14 (pre-migration)
user-service/build/resources/main/application.yml                spring.data.mongodb.uri: ${MONGO_URI:mongodb://localhost:27017/filesharex_users}
user-service/build/resources/main/logback-spring.xml             <logger name="org.springframework.data.mongodb" level="WARN"/>
```
All four services have the same state: the compiled `build/classes/**` are current (dated 2026-10-08, contain `jakarta/persistence`, no MongoDB references), but `build/libs/*.jar` and `build/resources/**` are dated 2026-09-14 — i.e. they were produced **before** the migration.

**Verified by inspecting the archive** (`user-service-1.0.0-SNAPSHOT.jar` → `BOOT-INF/classes/application.yml`): it still contains `spring.data.mongodb.uri`, bundles the MongoDB driver jars and does **not** contain `spring-data-jpa`.

**Root Cause:** Sources were migrated and re-compiled (`compileJava`), but `processResources` / `bootJar` were never re-run, so the packaged artifacts still ship the MongoDB `application.yml`, the MongoDB logback logger and the pre-migration code.

**Impact if the stack is started as-is:** `start-services.ps1` / `manage-project.ps1` launch the stale JARs, which open MongoDB connections (`MongoTimeoutException` / `UnknownHostException` style failures) and never touch PostgreSQL.

**Fix:** Rebuild all services before the next run (NOT executed in this pass):
```powershell
.\gradlew.bat clean bootJar --no-daemon
```
Optionally delete `*/build` and `*/bin` first to purge every pre-migration artifact.

---

### Issue 14.2: Dependency and Configuration Property Swap

**Root Cause:** Spring Data MongoDB artifacts were still declared / referenced while the code already used JPA annotations.

**Fix (applied):**
```groovy
// */build.gradle — before
implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'

// after
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
runtimeOnly 'org.postgresql:postgresql'
```
```yaml
# before
spring.data.mongodb.uri: ${MONGO_URI:mongodb://localhost:27017/filesharex_users}

# after
spring.datasource.url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5433/filesharex_users}
spring.datasource.username: ${SPRING_DATASOURCE_USERNAME:filesharex}
spring.datasource.password: ${SPRING_DATASOURCE_PASSWORD:filesharex}
spring.jpa.hibernate.ddl-auto: update
spring.jpa.open-in-view: false
```
`logback-spring.xml`: logger `org.springframework.data.mongodb` → `org.hibernate`.
`config-repo/*.yml`, `docker-compose.yml`, `start-services.ps1` and `manage-project.ps1` were switched to the same datasource properties / PostgreSQL containers.

---

### Issue 14.3: Repository API Migration (`MongoRepository` → `JpaRepository`)

**Root Cause:** Repository interfaces and derived query methods had Mongo semantics.

**Fix (applied):**
| Repository method | Migration note |
|-------------------|----------------|
| `findByClerkId(...)` returning a bare entity | JPA returns `null` when no row matches — same as Mongo, so the non-`Optional` signature was kept |
| `findByClerkIdOrderByTransactionDateDesc` | Derived `ORDER BY` supported by Spring Data JPA |
| `findByClerkIdAndReadFalseOrderByCreatedAtDesc` | Boolean property predicate (`read = false`) works with the Lombok-generated `isRead()` accessor |
| `countByClerkIdAndReadFalse` / `countByClerkId` | Return type `Long` (JPA `count()`), unchanged |
| Duplicate-key writes | JPA throws `DataIntegrityViolationException` / `DuplicateKeyException` (SQLState 23505), already handled by `user-service` `GlobalExceptionHandler` → HTTP 409 |

---

### Issue 14.4: Identifier Generation Changed (Mongo ObjectId → UUID)

**Root Cause:** MongoDB `_id` (24-hex `ObjectId`) has no JPA equivalent.

**Fix (applied):**
```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private String id;
```
Primary keys are now UUID strings (`550e8400-e29b-...`), not 24-character hex values.

**Impact:** Stored IDs created before the migration no longer exist in PostgreSQL (fresh schema — see 14.10), and any script/test that assumes an ObjectId format will fail. The React frontend was checked: it uses IDs as opaque strings, so no change was required.

---

### Issue 14.5: Table and Column Naming

**Root Cause:** Mongo collections (`file_metadata`, `transactions`) do not exist in the relational model, and camelCase fields must map to snake_case columns.

**Fix (applied):**
| Former collection | New table (`@Table`) |
|-------------------|----------------------|
| `profiles` | `profiles` |
| `user_credits` | `user_credits` |
| `file_metadata` | `files` |
| `transactions` | `payment_transactions` |
| `notifications` | `notifications` |

Hibernate 6's default `CamelCaseToUnderscoresNamingStrategy` maps `clerkId → clerk_id`, `createdAt → created_at`, `isPublic → is_public`, etc.

**Note:** the `notifications.read` column was checked against the PostgreSQL keyword list — `READ` is a **non-reserved** keyword (`kwlist.h`: `UNRESERVED_KEYWORD`), so it can be used unquoted. No renaming was needed.

**Cosmetic leftover (intentionally untouched):** entity classes still live in `document/` packages and are still named `*Document` (`ProfileDocument`, `FileMetadataDocument`). They are now `@Entity` classes; renaming packages was out of scope.

---

### Issue 14.6: Leftover Spring Data MongoDB Annotation `@CreatedDate`

**Symptom:**
```java
import org.springframework.data.annotation.CreatedDate;   // ProfileDocument, Notification
@CreatedDate
private Instant createdAt;
```

**Root Cause:** `@CreatedDate` is a Spring Data **MongoDB** auditing annotation. There is no `@EnableJpaAuditing` in any service, so under JPA it is a silent no-op.

**Impact:** None in practice — timestamps are written explicitly:
* `ProfileService.createProfile(...)` → `.createdAt(Instant.now())`
* `NotificationService.createNotification(...)` → `.createdAt(LocalDateTime.now())`
`mapToDTO` also guards against `null` (`notification.getCreatedAt() != null ? ... : null`).

**Recommended follow-up (not applied — keeps the diff minimal):** either add `@EnableJpaAuditing` + `AuditingEntityListener`, or replace `@CreatedDate` with `@PrePersist`.

---

### Issue 14.7: Unique Constraint Behaviour on `profiles.email`

**Root Cause:** `@Column(unique = true)` + `spring.jpa.hibernate.ddl-auto: update` creates a real unique index in PostgreSQL; a duplicate insert raises `ERROR: duplicate key value violates unique constraint ... (SQLState 23505)`.

**Fix:** Already handled — `user-service` `GlobalExceptionHandler` maps `DuplicateKeyException` → HTTP 409 CONFLICT (the Mongo equivalent was `E11000 duplicate key error`, see issue 3.2).

---

### Issue 14.8: Read-Modify-Write Is Not Transactional (Pre-existing, Not a Regression)

**Observation:** `UserCreditsService.consumeCredit(...)` / `addCredits(...)` and `PaymentService.updateTransactionStatus(...)` do *read → modify → save* without `@Transactional`; each repository call runs in its own transaction.

**Impact:** Concurrent credit consumption for the same user can lose an update (lost update). The MongoDB implementation had exactly the same pattern, so this is **not** introduced by the migration.

**Recommended follow-up:** annotate the mutating service methods with `@Transactional`, or add optimistic locking (`@Version`) to `UserCredits`.

---

### Issue 14.9: Docker Compose Infrastructure Rewrite

**Fix (applied):**
| Item | Before (Mongo) | After (PostgreSQL) |
|------|----------------|--------------------|
| Images | `mongo:7` ×4 | `postgres:17-alpine` ×4 |
| Service names | `mongo-users`, `mongo-files`, `mongo-payments`, `mongo-notifications` | `postgres-users`, `postgres-files`, `postgres-payments`, `postgres-notifications` |
| Host ports | 27017–27020 | 5433, 5434, 5435, 5436 (→ 5432 in container) |
| Databases | `filesharex_users/_files/_payments/_notifications` | unchanged names |
| Env vars | `MONGO_URI` | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `POSTGRES_PASSWORD` |
| Volumes | `mongo_users_data`, ... | `postgres_users_data`, `postgres_files_data`, `postgres_payments_data`, `postgres_notifications_data` |
| Healthcheck | (none) | `pg_isready -U filesharex -d <db>` + `depends_on: condition: service_healthy` |

**Operational notes:**
* Old `mongo-*` containers and `mongo_*` volumes are now orphaned — stop/remove them (`docker stop filesharex-mongo-users ...`, `docker volume rm mongo_users_data ...`) so ports 27017–27020 and disk space are freed.
* `.gitignore` / `.dockerignore` still referenced `mongo_data` → renamed to `postgres_*_data` (data lives in named Docker volumes, not a host folder).
* Local (non-Docker) runs use the host ports 5433–5436, matching `spring.datasource.url` defaults in each `application.yml`.

---

### Issue 14.10: No Data Migration Performed

**Root Cause:** The PostgreSQL schema is created empty by `spring.jpa.hibernate.ddl-auto: update`; MongoDB documents were not exported.

**Impact:** All pre-migration profiles, files, transactions and notifications are only inside the old `mongo_*` Docker volumes.

**Fix (manual, when required):** Mongo documents use camelCase field names, so a staging table is needed to map them to the snake_case columns:
```bash
mongoexport --db filesharex_users --collection profiles --type=csv \
  --fields id,clerkId,email,firstName,lastName,photoUrl,credits,createdAt --out profiles.csv
```
```sql
CREATE TABLE profiles_staging (id text, "clerkId" text, email text, "firstName" text,
                               "lastName" text, "photoUrl" text, credits int, "createdAt" timestamptz);
\copy profiles_staging FROM 'profiles.csv' WITH (FORMAT csv, HEADER true)
INSERT INTO profiles (id, clerk_id, email, first_name, last_name, photo_url, credits, created_at)
SELECT id, "clerkId", email, "firstName", "lastName", "photoUrl", credits, "createdAt"
FROM profiles_staging ON CONFLICT DO NOTHING;   -- profiles.email has a unique index
DROP TABLE profiles_staging;
```
Repeat for `user_credits`, `files`, `payment_transactions` and `notifications`. Otherwise start with a clean database (current state).

---

### Issue 14.11: HLD Documentation Out of Date

**Symptom:** All four `HLD/` documents still described MongoDB instances, ports 27017–27020, `mongo_*` volumes, "Collections" and `MONGO_URI`.

**Fix (applied) — files updated in `HLD/`:**
* `architecture-diagram.md` — data layer, dependency graph, request flow, technology stack, port map
* `deployment-architecture.md` — container registry, network topology, volumes, environment variables, traffic flow, production considerations
* `project-structure.md` — docker-compose service table, per-service database/table sections, config-repo descriptions, port map
* `README.md` — architecture diagram, data architecture tables, monitoring panel list, deployment sections

LLD, `README.md` (root), `docs/08-architecture-diagram.md` and other historical docs were intentionally left untouched.

---

### Issue 14.12: Verification Still Pending (Project Not Run)

The following commands were **deliberately not executed** and must be run before the migration is considered complete:
```powershell
# 1. Rebuild (fixes 14.1)
.\gradlew.bat clean bootJar --no-daemon

# 2. Start the databases
docker compose up -d postgres-users postgres-files postgres-payments postgres-notifications

# 3. Start the stack and check
docker compose ps
# then start-services.ps1 / manage-project.ps1 and hit /actuator/health on 8081-8084
```
Expected first-run failures if skipped: stale JARs reconnecting to MongoDB (14.1), `Failed to configure a DataSource` (missing datasource env), or `relation "profiles" does not exist` (DB not created yet).

---

## Updated Quick Reference: Monitoring Commands

### Check Prometheus Targets
```bash
# List all targets and their health
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {service: .labels.service, health: .health, error: .lastError}'

# Query a specific metric
curl 'http://localhost:9090/api/v1/query?query=process_uptime_seconds'
```

### Check Grafana
```bash
# Grafana health
curl http://localhost:3000/api/health

# Default credentials
# Username: admin
# Password: admin
```

### Restart Monitoring Stack
```bash
# Restart Prometheus
docker restart filesharex-prometheus

# Restart Grafana
docker restart filesharex-grafana

# Or full reset
docker rm -f filesharex-prometheus filesharex-grafana
docker network create filesharex-monitoring 2>/dev/null
docker run -d --name filesharex-prometheus --network filesharex-monitoring -p 9090:9090 \
  -v "./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml" \
  prom/prometheus:latest --config.file=/etc/prometheus/prometheus.yml
docker run -d --name filesharex-grafana --network filesharex-monitoring -p 3000:3000 \
  -e GF_SECURITY_ADMIN_USER=admin -e GF_SECURITY_ADMIN_PASSWORD=admin \
  -v "./monitoring/grafana/provisioning:/etc/grafana/provisioning" \
  -v "./monitoring/grafana/dashboards:/var/lib/grafana/dashboards" \
  grafana/grafana:latest
```

---

## 15. CI/CD & Cloud Deployment Issues

### Issue 15.1: Multi-Stage Docker Gradle Build Failure in Microservices Subprojects

**Error:**
```text
failed to solve: process "/bin/sh -c chmod +x gradlew && ./gradlew :eureka-server:bootJar --no-daemon" did not complete successfully: exit code: 1
Process exited with status 1
```

**Root Causes:**
1. **Multi-Project Missing Directories**: The root `settings.gradle` registers 7 subprojects (`eureka-server`, `config-server`, `gateway`, `user-service`, `file-service`, `payment-service`, `notification-service`). When individual service Dockerfiles only copied their own isolated subfolder (e.g. `COPY eureka-server/ ./eureka-server/`), Gradle evaluation failed because the sibling subproject paths referenced in `settings.gradle` did not exist inside the container workspace.
2. **CRLF Line Endings in Alpine**: On Windows workstations, Git checked out `gradlew` with Windows CRLF line endings (`\r\n`). In Alpine Linux containers, BusyBox `/bin/sh` cannot parse the `\r` carriage return in the shebang, causing `/bin/sh: ./gradlew: not found` or syntax failure (Exit code 1).
3. **Container Build Thrashing on EC2 (`t3.small`)**: Building 7 distinct Docker images in parallel—each spinning up an independent Gradle daemon and compiling Java 21 code inside the container—exhausted RAM and CPU on smaller cloud instances.

**Fix:**
1. **Host-Level Single Gradle Build**:
   Updated `.github/workflows/deploy-backend.yml` to compile all 7 service artifacts in a single host-level execution prior to container assembly:
   ```bash
   sed -i 's/\r$//' gradlew
   chmod +x gradlew
   ./gradlew bootJar --no-daemon
   ```
   This compiles all microservice JARs in ~22 seconds using a unified build cache, avoiding redundant Gradle downloads and memory thrashing.
2. **Single-Stage Lightweight Dockerfiles**:
   Converted all 7 Dockerfiles to lightweight single-stage `eclipse-temurin:21-jre-alpine` runtime images that simply copy their respective pre-built `build/libs/*SNAPSHOT.jar`. Container creation reduced from ~5 minutes to under 3 seconds per service.
3. **Enforced Linux Line Endings (`.gitattributes`)**:
   Created a repository-level `.gitattributes` file:
   ```gitattributes
   * text=auto eol=lf
   *.bat text eol=crlf
   *.ps1 text eol=crlf
   gradlew text eol=lf
   *.sh text eol=lf
   ```
4. **JVM Memory Limits for Cloud Free Tier / Credits (`t3.small`)**:
   Injected `JAVA_TOOL_OPTIONS: "-Xms64m -Xmx180m -XX:+UseG1GC"` into `docker-compose.yml` across all Java microservices to cap heap allocation within 2 GB instance constraints.

---

### Issue 15.2: Missing Gradle Wrapper JAR in Git Repository

**Error:**
```text
Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain
Caused by: java.lang.ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain
Process exited with status 1
```

**Root Cause:**
The root `.gitignore` contained a wildcard rule `*.jar` intended to ignore compiled Java application packages, but this unintentionally ignored the essential `gradle/wrapper/gradle-wrapper.jar` binary as well. When the EC2 instance cloned the repository, the wrapper script `./gradlew` had no entrypoint JAR to execute.

**Fix:**
1. Whitelisted the wrapper JAR in `.gitignore`:
   ```gitignore
   *.jar
   !gradle/wrapper/gradle-wrapper.jar
   ```
2. Force-tracked the binary into Git:
   ```bash
   git add -f gradle/wrapper/gradle-wrapper.jar
   git commit -m "fix: include gradle-wrapper.jar in repository"
   ```



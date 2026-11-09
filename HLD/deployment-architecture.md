# Deployment Architecture

## 1. Deployment Modes

### 1.1 Microservices (Docker Compose) - Active

Full microservices deployment with 14 containers.

### 1.2 Monolith (Render) - Legacy

Single backend + static frontend on Render.

---

## 2. Docker Compose Architecture

### 2.1 Container Registry

| Container | Image | Port | Purpose |
|-----------|-------|------|---------|
| filesharex-eureka | eclipse-temurin:21-jre-alpine | 8761 | Service Registry |
| filesharex-config | eclipse-temurin:21-jre-alpine | 9999 | Config Server |
| filesharex-gateway | eclipse-temurin:21-jre-alpine | 8080 | API Gateway |
| filesharex-user-service | eclipse-temurin:21-jre-alpine | 8081 | User Management |
| filesharex-file-service | eclipse-temurin:21-jre-alpine | 8082 | File Operations |
| filesharex-payment-service | eclipse-temurin:21-jre-alpine | 8083 | Payment Processing |
| filesharex-notification-service | eclipse-temurin:21-jre-alpine | 8084 | Notifications |
| filesharex-frontend | nginx:1.27-alpine | 5174→80 | React SPA |
| filesharex-postgres-users | postgres:17-alpine | 5433→5432 | User Database |
| filesharex-postgres-files | postgres:17-alpine | 5434→5432 | File Database |
| filesharex-postgres-payments | postgres:17-alpine | 5435→5432 | Payment Database |
| filesharex-postgres-notifications | postgres:17-alpine | 5436→5432 | Notification Database |
| filesharex-prometheus | prom/prometheus:latest | 9090 | Metrics |
| filesharex-grafana | grafana/grafana:latest | 3000 | Dashboards |

### 2.2 Network Topology

```
┌─────────────────────────────────────────────────────────────────┐
│                    Docker Host Machine                          │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              filesharex-monitoring network                │  │
│  │  ┌──────────────┐  ┌──────────────┐                      │  │
│  │  │  Prometheus  │  │   Grafana    │                      │  │
│  │  │   :9090      │◄─│   :3000      │                      │  │
│  │  └──────────────┘  └──────────────┘                      │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              Default bridge network                       │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐    │  │
│  │  │ Eureka   │ │ Config   │ │ Gateway  │ │ Frontend │    │  │
│  │  │ :8761    │ │ :9999    │ │ :8080    │ │ :5174    │    │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘    │  │
│  │                                                           │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐    │  │
│  │  │User Svc  │ │File Svc  │ │Payment   │ │Notif Svc │    │  │
│  │  │:8081     │ │:8082     │ │:8083     │ │:8084     │    │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘    │  │
│  │                                                           │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐    │  │
│  │  │Postgres  │ │Postgres  │ │Postgres  │ │Postgres  │    │  │
│  │  │Users     │ │Files     │ │Payments  │ │Notifs    │    │  │
│  │  │:5433     │ │:5434     │ │:5435     │ │:5436     │    │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘    │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.3 Health Checks

| Service | Health Check | Interval | Timeout | Retries |
|---------|--------------|----------|---------|---------|
| Eureka Server | `/actuator/health` | 30s | 10s | 3 |
| Config Server | `/actuator/health` | 30s | 10s | 3 |

### 2.4 Startup Dependencies

```yaml
eureka-server:
  healthcheck: /actuator/health

config-server:
  depends_on: eureka-server (service_healthy)

gateway:
  depends_on: eureka-server (service_healthy), config-server (service_healthy)

business-services:
  depends_on: eureka-server (service_healthy), config-server (service_healthy)

frontend:
  depends_on: gateway
```

---

## 3. Render Blueprint (Legacy)

### 3.1 Services

| Service | Type | Runtime | Root |
|---------|------|---------|------|
| filesharex-backend | web | docker | backend |
| filesharex-frontend | web | static | frontend |

### 3.2 Backend Configuration

```yaml
Runtime: Docker
Health: /api/v1.0/health
Environment:
  - SPRING_DATASOURCE_URL (PostgreSQL JDBC URL)
  - CLERK_ISSUER
  - CLERK_JWKS_URL
  - CLERK_WEBHOOK_SECRET
  - RAZORPAY_KEY_ID
  - RAZORPAY_KEY_SECRET
```

### 3.3 Frontend Configuration

```yaml
Runtime: Static
Build: npm ci && npm run build
Publish: dist
SPA Rewrite: /index.html
Environment:
  - VITE_CLERK_PUBLISHABLE_KEY
  - VITE_RAZORPAY_KEY
  - VITE_API_BASE_URL (points to backend Render URL)
```

---

## 4. Volume Management

### 4.1 Persistent Volumes

| Volume | Container | Purpose |
|--------|-----------|---------|
| postgres_users_data | filesharex-postgres-users | User database persistence |
| postgres_files_data | filesharex-postgres-files | File database persistence |
| postgres_payments_data | filesharex-postgres-payments | Payment database persistence |
| postgres_notifications_data | filesharex-postgres-notifications | Notification database persistence |
| prometheus_data | filesharex-prometheus | Metrics retention (15 days) |
| grafana_data | filesharex-grafana | Dashboard persistence |

---

## 5. Environment Variables

### 5.1 Backend Services

| Variable | Service | Purpose |
|----------|---------|---------|
| `SPRING_DATASOURCE_URL` | User/File/Payment/Notification | PostgreSQL JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | User/File/Payment/Notification | PostgreSQL login user |
| `SPRING_DATASOURCE_PASSWORD` | User/File/Payment/Notification | PostgreSQL login password |
| `POSTGRES_PASSWORD` | Docker Compose (PostgreSQL) | Password for the `filesharex` DB role |
| `CLERK_ISSUER` | Gateway, User Service | Clerk JWT issuer |
| `CLERK_JWKS_URL` | Gateway, User Service | Clerk JWKS endpoint |
| `CLERK_WEBHOOK_SECRET` | User Service | Webhook signature verification |
| `RAZORPAY_KEY_ID` | Payment Service | Razorpay API key |
| `RAZORPAY_KEY_SECRET` | Payment Service | Razorpay API secret |
| `FILE_STORAGE_TYPE` | File Service | `local` or `r2` |
| `R2_ENDPOINT` | File Service | Cloudflare R2 endpoint |
| `R2_ACCESS_KEY` | File Service | R2 access key |
| `R2_SECRET_KEY` | File Service | R2 secret key |
| `R2_BUCKET_NAME` | File Service | R2 bucket name |
| `R2_PUBLIC_URL` | File Service | R2 public CDN URL |
| `USER_SERVICE_URL` | File, Payment Service | User service internal URL |
| `MAIL_HOST` | Notification Service | SMTP host |
| `MAIL_PORT` | Notification Service | SMTP port |
| `MAIL_USERNAME` | Notification Service | SMTP username |
| `MAIL_PASSWORD` | Notification Service | SMTP password |

### 5.2 Frontend

| Variable | Purpose |
|----------|---------|
| `VITE_CLERK_PUBLISHABLE_KEY` | Clerk frontend SDK key |
| `VITE_RAZORPAY_KEY` | Razorpay frontend key |
| `VITE_API_BASE_URL` | Gateway URL for API calls |

---

## 6. Traffic Flow

```
User Browser
    │
    ▼
┌─────────────────────────────────────────────────┐
│  1. Frontend (React/Vite)                       │
│     - User authenticates via Clerk              │
│     - Obtains JWT token                         │
│     - Sends API request with Bearer token       │
└───────────────────────┬─────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────┐
│  2. API Gateway (Port 8080)                     │
│     - Validates JWT via Clerk JWKS              │
│     - Extracts clerkId                          │
│     - Routes to appropriate service             │
│     - Injects X-Clerk-Id header                 │
└───────────────────────┬─────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ User Service │ │ File Service │ │Payment Service│
│   :8081      │ │   :8082      │ │   :8083      │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                │
       ▼                ▼                ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│Postgres Users│ │Postgres Files│ │Postgres Payts│
│   :5433      │ │   :5434      │ │   :5435      │
└──────────────┘ └──────┬───────┘ └──────────────┘
                        │
                        ▼
                 ┌──────────────┐
                 │ Cloudflare R2│
                 │ (File Store) │
                 └──────────────┘
```

---

## 7. Monitoring Deployment

### 7.1 Prometheus Configuration

```yaml
scrape_interval: 15s
retention: 15 days

targets:
  - host.docker.internal:8080/actuator/prometheus (Gateway)
  - host.docker.internal:8081/api/v1.0/actuator/prometheus (User)
  - host.docker.internal:8082/api/v1.0/actuator/prometheus (File)
  - host.docker.internal:8083/api/v1.0/actuator/prometheus (Payment)
  - host.docker.internal:8084/api/v1.0/actuator/prometheus (Notification)
  - host.docker.internal:8761/actuator/prometheus (Eureka)
```

### 7.2 Grafana Configuration

```yaml
Datasource: http://host.docker.internal:9090
Dashboard: FileShareX Microservices Dashboard
Credentials: admin/admin
```

---

## 8. Deployment Commands

### 8.1 Full Stack (Docker Compose)

```bash
# Build and start all services
docker-compose up --build -d

# Check status
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### 8.2 Local Development

```bash
# Build all services
./gradlew clean build

# Start infrastructure first
java -jar eureka-server/build/libs/eureka-server-1.0.0-SNAPSHOT.jar
java -jar config-server/build/libs/config-server-1.0.0-SNAPSHOT.jar

# Start business services
java -jar gateway/build/libs/gateway-1.0.0-SNAPSHOT.jar
java -jar user-service/build/libs/user-service-1.0.0-SNAPSHOT.jar
java -jar file-service/build/libs/file-service-1.0.0-SNAPSHOT.jar
java -jar payment-service/build/libs/payment-service-1.0.0-SNAPSHOT.jar
java -jar notification-service/build/libs/notification-service-1.0.0-SNAPSHOT.jar

# Start frontend
cd frontend && npm run dev
```

### 8.3 Monitoring Only

```bash
# Start Prometheus + Grafana
docker network create filesharex-monitoring
docker run -d --name filesharex-prometheus --network filesharex-monitoring -p 9090:9090 \
  -v "./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml" \
  prom/prometheus:latest

docker run -d --name filesharex-grafana --network filesharex-monitoring -p 3000:3000 \
  -e GF_SECURITY_ADMIN_USER=admin -e GF_SECURITY_ADMIN_PASSWORD=admin \
  -v "./monitoring/grafana/provisioning:/etc/grafana/provisioning" \
  -v "./monitoring/grafana/dashboards:/var/lib/grafana/dashboards" \
  grafana/grafana:latest
```

---

## 9. Production Considerations

| Concern | Current | Recommendation |
|---------|---------|----------------|
| Service Discovery | Single Eureka | Eureka集群 for HA |
| Config Server | Single instance | Multiple instances + Git backend |
| Database | Single PostgreSQL per service | Primary/replica streaming + backups |
| Load Balancing | Client-side (Ribbon) | Consider nginx/HAProxy |
| SSL/TLS | Not configured | Add SSL termination at gateway |
| Rate Limiting | Not implemented | Add via gateway filters |
| Centralized Logging | Local files only | ELK stack or Loki |
| Alerting | Not configured | Prometheus AlertManager |

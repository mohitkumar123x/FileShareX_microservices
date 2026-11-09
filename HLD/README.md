# FileShareX High Level Design (HLD)

## 1. Objective
FileShareX is a full-stack file sharing platform where users can:
- Authenticate via Clerk
- Upload/manage files
- Share files publicly through links
- Buy credits through Razorpay
- Monitor system health via Prometheus + Grafana

---

## 2. Architecture Overview

### 2.1 Deployment Modes

| Mode | Description | Status |
|------|-------------|--------|
| **Monolith** | Single Spring Boot backend + React frontend on Render | Legacy |
| **Microservices** | 7 Java services + React frontend on Docker Compose | Active |

### 2.2 Microservices Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         EXTERNAL SERVICES                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │  Clerk   │  │ Razorpay │  │ Cloudflare│  │  Gmail   │               │
│  │ (Auth)   │  │(Payments)│  │ R2 Storage│  │  (SMTP)  │               │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘               │
└───────┼──────────────┼──────────────┼──────────────┼────────────────────┘
        │              │              │              │
        ▼              ▼              ▼              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY (Port 8080)                              │
│  Spring Cloud Gateway (WebFlux/Netty)                                  │
│  • JWT Auth (Clerk JWKS)  • Route Matching  • CORS                     │
│  • StripPrefix=2          • Load Balancing                              │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ USER SERVICE │  │ FILE SERVICE │  │PAYMENT SERVICE│  │NOTIFICATION  │
│   (8081)     │  │   (8082)     │  │   (8083)     │  │SERVICE(8084) │
│              │  │              │  │              │  │              │
│ • Profiles   │  │ • Upload     │  │ • Orders     │  │ • Email      │
│ • Credits    │  │ • Download   │  │ • Verify     │  │   Notify     │
│ • Webhooks   │  │ • R2 Storage │  │ • Transactions│  │              │
└──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────────────┘
       │                 │                 │
       ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│Postgres Users│  │Postgres Files│  │Postgres Payts│
│  (5433)      │  │  (5434)      │  │  (5435)      │
└──────────────┘  └──────────────┘  └──────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                    INFRASTRUCTURE SERVICES                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │ Eureka Server│  │Config Server │  │  Prometheus  │  ┌────────────┐  │
│  │   (8761)     │  │   (9999)     │  │   (9090)     │  │  Grafana   │  │
│  │              │  │              │  │              │  │   (3000)   │  │
│  │• Service     │  │• Centralized │  │• Metrics     │  │• Dashboard │  │
│  │  Registry    │  │  Config      │  │  Scraping    │  │• Visualize │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Services

### 3.1 Service Registry

| Service | Port | Type | Technology |
|---------|------|------|------------|
| Eureka Server | 8761 | Infrastructure | Spring Cloud Netflix |
| Config Server | 9999 | Infrastructure | Spring Cloud Config |
| API Gateway | 8080 | Infrastructure | Spring Cloud Gateway |
| User Service | 8081 | Business | Spring Boot |
| File Service | 8082 | Business | Spring Boot |
| Payment Service | 8083 | Business | Spring Boot |
| Notification Service | 8084 | Business | Spring Boot |
| Frontend | 5173/5174 | Presentation | React + Vite |

### 3.2 Service Dependencies

```
Startup Order:
  Eureka Server → Config Server → [All Business Services] → Gateway → Frontend

Runtime Dependencies:
  Gateway → Eureka (discovery)
  All Services → Eureka (registration)
  All Services → Config Server (configuration)
  File Service → User Service (credits check, circuit breaker)
  Payment Service → User Service (user validation, circuit breaker)
```

---

## 4. Data Architecture

### 4.1 Database-per-Service Pattern

| Service | Database | Host Port | Tables |
|---------|----------|-----------|--------|
| User Service | filesharex_users | 5433 | profiles, user_credits |
| File Service | filesharex_files | 5434 | files |
| Payment Service | filesharex_payments | 5435 | payment_transactions |
| Notification Service | filesharex_notifications | 5436 | notifications |

### 4.2 External Storage

| System | Purpose | Access Pattern |
|--------|---------|----------------|
| Cloudflare R2 | File storage (S3-compatible) | Upload/Download via AWS SDK v2 |
| PostgreSQL (per service) | Relational persistence | Spring Data JPA (Hibernate) |

---

## 5. API Gateway Routes

| Gateway Path | Target Service | Methods |
|--------------|----------------|---------|
| `/api/v1.0/users/**` | User Service (8081) | GET, POST, PATCH |
| `/api/v1.0/webhooks/**` | User Service (8081) | POST |
| `/api/v1.0/files/**` | File Service (8082) | GET, POST, PATCH, DELETE |
| `/api/v1.0/payments/**` | Payment Service (8083) | POST |
| `/api/v1.0/transactions/**` | Payment Service (8083) | GET |
| `/api/v1.0/notifications/**` | Notification Service (8084) | GET |
| `/health` | Gateway (direct) | GET |
| `/actuator/**` | All services | GET |

---

## 6. Security Architecture

### 6.1 Authentication Flow

```
Frontend → Clerk SDK → JWT Token
         → Gateway (ClerkJwtAuthFilter)
           → Validates JWT via Clerk JWKS
           → Extracts clerkId (sub claim)
           → Injects X-Clerk-Id header
         → Backend Service
           → Reads X-Clerk-Id header
```

### 6.2 Public Endpoints (No Auth)

- `/webhooks/**` - Clerk webhook events
- `/files/public/**` - Public file access
- `/files/download/**` - Public file download
- `/health` - Health checks
- `/actuator/**` - Monitoring endpoints

### 6.3 Security Layers

| Layer | Component | Protection |
|-------|-----------|------------|
| Gateway | ClerkJwtAuthFilter | JWT validation via JWKS |
| Gateway | SecurityConfig | Path matching + permitAll |
| Services | X-Clerk-Id header | User identification |
| Webhooks | Svix signatures | Webhook authenticity |

---

## 7. External Integrations

### 7.1 Clerk (Authentication)

| Component | Purpose |
|-----------|---------|
| JWT Validation | Gateway validates tokens via JWKS endpoint |
| Webhooks | User lifecycle events (created/updated/deleted) |
| Frontend SDK | `@clerk/clerk-react` for sign-in/sign-out |

### 7.2 Razorpay (Payments)

| Component | Purpose |
|-----------|---------|
| Order Creation | `POST /payments/create-order` |
| Payment Verification | HMAC-SHA256 signature verification |
| Credits Allocation | Automatic credit addition on successful payment |

### 7.3 Cloudflare R2 (File Storage)

| Component | Purpose |
|-----------|---------|
| Upload | S3-compatible PUT operation |
| Download | S3-compatible GET operation |
| Delete | S3-compatible DELETE operation |
| Public URLs | CDN-based file access |

### 7.4 SMTP/Gmail (Notifications)

| Component | Purpose |
|-----------|---------|
| Email Service | Spring Boot Mail (SMTP) |
| Transport | TLS/STARTTLS |
| Use Case | File sharing notifications |

---

## 8. Monitoring & Observability

### 8.1 Metrics Collection

| Component | Technology | Purpose |
|-----------|------------|---------|
| Metrics | Micrometer + Prometheus | JVM, HTTP, custom metrics |
| Tracing | Micrometer Tracing + Brave | Distributed trace correlation |
| Logging | Logback + Logstash Encoder | Structured JSON logs |

### 8.2 Monitoring Stack

| Tool | Port | Purpose |
|------|------|---------|
| Prometheus | 9090 | Metrics scraping (15s interval) |
| Grafana | 3000 | Dashboard visualization |

### 8.3 Grafana Dashboard Panels

- Request Rate (per service)
- Response Time (p95)
- Error Rate (%)
- JVM Memory Usage
- Circuit Breaker State
- PostgreSQL Connections (HikariCP pool)

---

## 9. Resilience Patterns

### 9.1 Circuit Breaker (Resilience4j)

| Service | Target | Failure Threshold | Wait Duration |
|---------|--------|-------------------|---------------|
| File Service | User Service | 50% | 30s |
| Payment Service | User Service | 50% | 30s |

### 9.2 Retry & Timeout

- Sliding window size: 10 calls
- Permitted calls in half-open: 3
- User service client timeout: configured per service

---

## 10. Frontend Architecture

### 10.1 Tech Stack

| Component | Technology |
|-----------|------------|
| Framework | React 18.2 |
| Build Tool | Vite 5.2 |
| CSS | Tailwind CSS 4.1 |
| Auth | Clerk React SDK 5.35.4 |
| HTTP | Axios 1.11 |
| Routing | React Router DOM 7.7 |
| Notifications | react-hot-toast |

### 10.2 Routes

| Route | Component | Auth Required |
|-------|-----------|---------------|
| `/` | Landing | No |
| `/dashboard` | Dashboard | Yes |
| `/upload` | Upload | Yes |
| `/my-files` | MyFiles | Yes |
| `/subscriptions` | Subscription | Yes |
| `/transactions` | Transactions | Yes |
| `/file/:fileId` | PublicFileView | No |

---

## 11. Deployment

### 11.1 Docker Compose (Microservices)

```yaml
Services:
  Infrastructure: Eureka, Config Server, Gateway
  Business: User, File, Payment, Notification
  Databases: PostgreSQL x4 (postgres:17-alpine, database-per-service)
  Monitoring: Prometheus, Grafana
  Frontend: React (Nginx)
```

### 11.2 Render (Legacy Monolith)

```yaml
Services:
  Backend: Spring Boot (Docker)
  Frontend: Static site (Nginx)
  Database: PostgreSQL (external managed instance)
```

---

## 12. Non-Functional Requirements

| Requirement | Implementation |
|-------------|----------------|
| Scalability | Database-per-service, independent scaling |
| Availability | Eureka health checks, circuit breakers |
| Security | JWT auth, HTTPS, CORS configuration |
| Observability | Prometheus metrics, distributed tracing |
| Resilience | Circuit breakers, retry policies |
| Maintainability | Centralized config, structured logging |

---

## 13. Risks & Considerations

| Risk | Mitigation |
|------|------------|
| Single point of failure (Eureka) | Consider Eureka集群 for production |
| No message queue | Synchronous only; add RabbitMQ/Kafka for async |
| No distributed transactions | Saga pattern not implemented |
| CORS permissive (`*`) | Restrict in production |
| Config Server Prometheus exclusion | Known Spring Cloud limitation |
| Docker DNS issues | Use `host.docker.internal` for cross-container communication |

# FileShareX Low Level Design (LLD) - Microservices Architecture

## 1. System Architecture Overview

```
┌──────────────┐              ┌─────────────────┐
│   Frontend   │ ──────────▶  │    Gateway      │ :8080
│   :5174      │              │  (JWT Auth)     │
└──────────────┘              └────────┬────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                  │
           ┌────────▼──────┐  ┌───────▼───────┐  ┌──────▼──────┐
           │  User Service │  │  File Service  │  │Payment Svc  │
           │  :8081        │  │  :8082         │  │:8083        │
           └───────┬───────┘  └───────┬───────┘  └──────┬──────┘
                   │                  │                  │
           ┌───────▼───────┐  ┌───────▼───────┐  ┌──────▼──────┐
           │   MongoDB     │  │   MongoDB     │  │  MongoDB    │
           │ filesharex_   │  │ filesharex_   │  │ filesharex_ │
           │ users         │  │ files         │  │ payments    │
           └───────────────┘  └───────┬───────┘  └─────────────┘
                                      │
                              ┌───────▼───────┐
                              │ Cloudflare R2 │
                              │ (S3 Storage)  │
                              └───────────────┘
```

## 2. Service Breakdown

| Service | Port | Responsibility | Database |
|---------|------|---------------|----------|
| **Gateway** | 8080 | JWT auth, routing, CORS | None |
| **User Service** | 8081 | Profile, credits, Clerk webhooks | filesharex_users |
| **File Service** | 8082 | File upload/download, metadata | filesharex_files |
| **Payment Service** | 8083 | Razorpay integration, transactions | filesharex_payments |

## 3. Inter-Service Communication

- **Protocol:** REST (synchronous)
- **Service Discovery:** Netflix Eureka
- **Config Management:** Spring Cloud Config Server
- **Fault Tolerance:** Resilience4j Circuit Breaker

### Internal API Endpoints (User Service)
```
GET  /internal/users/{clerkId}              → Profile DTO
GET  /internal/users/{clerkId}/credits      → Integer
POST /internal/users/{clerkId}/credits/consume → {credits, plan}
POST /internal/users/{clerkId}/credits/add     → {credits, plan}
```

## 4. Data Model

### User Service Collections

#### profiles
```json
{
  "_id": "string",
  "clerkId": "string (unique)",
  "email": "string (unique)",
  "firstName": "string",
  "lastName": "string",
  "credits": "integer",
  "photoUrl": "string",
  "createdAt": "Instant"
}
```

#### user_credits
```json
{
  "_id": "string",
  "clerkId": "string",
  "credits": "integer",
  "plan": "BASIC | PREMIUM | ULTIMATE"
}
```

### File Service Collections

#### files
```json
{
  "_id": "string",
  "name": "string",
  "type": "string (MIME)",
  "size": "long",
  "clerkId": "string",
  "isPublic": "boolean",
  "fileLocation": "string (R2 key)",
  "uploadedAt": "LocalDateTime"
}
```

### Payment Service Collections

#### payment_transactions
```json
{
  "_id": "string",
  "clerkId": "string",
  "orderId": "string",
  "paymentId": "string",
  "planId": "string",
  "amount": "integer (paise)",
  "currency": "string",
  "creditsAdded": "integer",
  "status": "PENDING | SUCCESS | FAILED | ERROR",
  "transactionDate": "LocalDateTime",
  "userEmail": "string",
  "userName": "string"
}
```

## 5. Security Design

### Gateway (Single Auth Entry Point)
- Clerk JWT validation via `ClerkJwtAuthFilter`
- JWKS key rotation with 1-hour cache
- Stateless session management

### Service-to-Service
- Internal endpoints (`/internal/**`) bypass JWT validation
- Services trust calls from Gateway only

### Endpoint Security Matrix

| Endpoint | Auth Required |
|----------|--------------|
| `/health` | No |
| `/webhooks/**` | No (webhook signature) |
| `/files/public/**` | No |
| `/files/download/**` | No |
| `/internal/**` | Service-only |
| All others | Yes (JWT) |

## 6. File Storage Architecture

### Cloudflare R2 Integration
- **Protocol:** S3-compatible API
- **Endpoint:** `https://{accountId}.r2.cloudflarestorage.com`
- **Region:** `auto`
- **Key Pattern:** `uploads/{clerkId}/{uuid}.{extension}`

### Storage Strategy Pattern
- `r2` → Cloudflare R2 (production)
- `local` → Local filesystem (development)

## 7. Config & Environment Variables

### Infrastructure Services
| Variable | Service | Default |
|----------|---------|---------|
| `CLERK_ISSUER` | Gateway, User | — |
| `CLERK_JWKS_URL` | Gateway, User | — |
| `CLERK_WEBHOOK_SECRET` | User | — |

### File Service
| Variable | Default |
|----------|---------|
| `FILE_STORAGE_TYPE` | `r2` |
| `R2_ENDPOINT` | — |
| `R2_ACCESS_KEY` | — |
| `R2_SECRET_KEY` | — |
| `R2_BUCKET_NAME` | `filesharex-uploads` |
| `R2_PUBLIC_URL` | — |

### Payment Service
| Variable | Default |
|----------|---------|
| `PAYMENT_PROVIDER` | `razorpay` |
| `RAZORPAY_KEY_ID` | — |
| `RAZORPAY_KEY_SECRET` | — |

### Databases
| Variable | Service | Default |
|----------|---------|---------|
| `MONGO_URI` | User | `mongodb://localhost:27017/filesharex_users` |
| `MONGO_URI` | File | `mongodb://localhost:27017/filesharex_files` |
| `MONGO_URI` | Payment | `mongodb://localhost:27017/filesharex_payments` |

## 8. Monitoring & Observability

### Actuator Endpoints (All Services)
- `/actuator/health` — Service health
- `/actuator/prometheus` — Metrics export
- `/actuator/metrics` — JVM, HTTP metrics

### Structured Logging
- JSON format via LogstashEncoder
- MDC fields: `serviceName`, `traceId`, `spanId`, `clerkId`
- Correlation across services via trace ID

### Prometheus Scrape Targets
- Gateway: `8080/actuator/prometheus`
- User Service: `8081/actuator/prometheus`
- File Service: `8082/actuator/prometheus`
- Payment Service: `8083/actuator/prometheus`

### Grafana Dashboard
- Request rate per service
- Response time (p50, p95, p99)
- Error rate
- JVM memory usage
- Circuit breaker state

## 9. Sequence Diagrams

### File Upload Flow
```
Frontend → Gateway → File Service → User Service → R2 Storage
    │          │           │             │            │
    │──POST───▶│           │             │            │
    │          │──JWT──────▶             │            │
    │          │           │──Credits────▶            │
    │          │           │             │            │
    │          │           │◀──OK────────│            │
    │          │           │                         │
    │          │           │────Upload───────────────▶│
    │          │           │                         │
    │          │           │◀──Key───────────────────│
    │          │           │                         │
    │          │◀──────────│                         │
    │◀─────────│           │                         │
```

### Payment Flow
```
Frontend → Gateway → Payment Service → User Service
    │          │           │                │
    │──POST───▶│           │                │
    │          │──JWT──────▶                │
    │          │           │                │
    │          │           │──Add Credits───▶│
    │          │           │                │
    │          │           │◀──Updated──────│
    │          │◀──────────│                │
    │◀─────────│           │                │
```

## 10. Docker Compose Services

| Service | Image | Port |
|---------|-------|------|
| eureka-server | Custom | 8761 |
| config-server | Custom | 9999 |
| gateway | Custom | 8080 |
| user-service | Custom | 8081 |
| file-service | Custom | 8082 |
| payment-service | Custom | 8083 |
| mongo-users | mongo:7 | 27017 |
| mongo-files | mongo:7 | 27018 |
| mongo-payments | mongo:7 | 27019 |
| prometheus | prom/prometheus | 9090 |
| grafana | grafana/grafana | 3000 |
| frontend | Custom | 5174 |

# FileShareX Microservices Architecture

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              EXTERNAL SERVICES                                                  │
│                                                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                        │
│  │    Clerk      │  │   Razorpay   │  │ Cloudflare   │  │   Gmail      │                        │
│  │  (Auth/JWT)   │  │  (Payments)  │  │    R2        │  │   (SMTP)     │                        │
│  │               │  │              │  │ (File Store)  │  │              │                        │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                        │
│         │                  │                  │                  │                                │
└─────────┼──────────────────┼──────────────────┼──────────────────┼────────────────────────────────┘
          │                  │                  │                  │
          │ Webhooks         │ Orders           │ S3 API           │ SMTP
          │ + JWT            │ + Verify         │ Upload/Download  │
          ▼                  ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              GATEWAY (Port 8080)                                                │
│  ┌─────────────────────────────────────────────────────────────────────────────────────────┐    │
│  │  Spring Cloud Gateway (WebFlux/Netty)                                                 │    │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐                           │    │
│  │  │ ClerkJwtAuth   │  │ Route Matching │  │   CORS         │                           │    │
│  │  │ Filter         │  │ + StripPrefix  │  │   Filter       │                           │    │
│  │  └────────────────┘  └────────────────┘  └────────────────┘                           │    │
│  │                                                                                       │    │
│  │  Routes:                                                                              │    │
│  │    /api/v1.0/users/**      → lb://user-service      (StripPrefix=2)                  │    │
│  │    /api/v1.0/webhooks/**   → lb://user-service      (StripPrefix=2)                  │    │
│  │    /api/v1.0/files/**      → lb://file-service      (StripPrefix=2)                  │    │
│  │    /api/v1.0/payments/**   → lb://payment-service   (StripPrefix=2)                  │    │
│  │    /api/v1.0/transactions/** → lb://payment-service  (StripPrefix=2)                  │    │
│  │    /api/v1.0/notifications/** → lb://notification-service (StripPrefix=2)             │    │
│  └─────────────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                                 │
│  Security: Clerk JWT validation via JWKS endpoint                                               │
│  Injects: X-Clerk-Id header to downstream services                                              │
└───────────────────────────────┬─────────────────────────────────────────────────────────────────┘
                                │
                                │ HTTP (via Eureka Service Discovery)
                                ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                            BUSINESS SERVICES                                                    │
│                                                                                                 │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐  ┌────────────────┐  │
│  │   USER SERVICE      │  │   FILE SERVICE       │  │  PAYMENT SERVICE    │  │ NOTIFICATION   │  │
│  │   (Port 8081)       │  │   (Port 8082)        │  │  (Port 8083)        │  │ SERVICE        │  │
│  │                     │  │                      │  │                     │  │ (Port 8084)    │  │
│  │  • Clerk Webhooks   │  │  • File Upload       │  │  • Razorpay Orders  │  │                │  │
│  │  • Profile Mgmt     │  │  • File Download     │  │  • Payment Verify   │  │  • Email       │  │
│  │  • Credits Mgmt     │  │  • File Metadata     │  │  • Transactions     │  │    Notify      │  │
│  │  • User CRUD        │  │  • R2 Storage        │  │  • User Credits     │  │                │  │
│  │                     │  │  • Circuit Breaker   │  │  • Circuit Breaker  │  │                │  │
│  └─────────┬───────────┘  └─────────┬────────────┘  └─────────┬───────────┘  └────────┬───────┘  │
│            │                        │                         │                        │          │
│            │ REST (circuit breaker)  │ REST (circuit breaker)  │                        │          │
│            ◄────────────────────────┼─────────────────────────┘                        │          │
│            │                        │                                                  │          │
└────────────┼────────────────────────┼──────────────────────────────────────────────────┼──────────┘
             │                        │                                                  │
             ▼                        ▼                                                  ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              DATA LAYER                                                         │
│                                                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐        │
│  │  MongoDB Users   │  │  MongoDB Files   │  │  MongoDB Payts   │  │  MongoDB Notifs  │        │
│  │  (Port 27017)    │  │  (Port 27018)    │  │  (Port 27019)    │  │  (Port 27020)    │        │
│  │                  │  │                  │  │                  │  │                  │        │
│  │  DB: filesharex  │  │  DB: filesharex  │  │  DB: filesharex  │  │  DB: filesharex  │        │
│  │      _users      │  │      _files      │  │      _payments   │  │      _notifs     │        │
│  │                  │  │                  │  │                  │  │                  │        │
│  │  Collections:    │  │  Collections:    │  │  Collections:    │  │  Collections:    │        │
│  │  • profiles      │  │  • file_metadata │  │  • transactions  │  │  • notifications │        │
│  │  • user_credits  │  │                  │  │                  │  │                  │        │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘  └──────────────────┘        │
│                                                                                                 │
│  Volumes: mongo_users_data, mongo_files_data, mongo_payments_data, mongo_notifications_data     │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                         INFRASTRUCTURE SERVICES                                                 │
│                                                                                                 │
│  ┌──────────────────────────┐  ┌──────────────────────────┐                                    │
│  │    EUREKA SERVER          │  │    CONFIG SERVER          │                                   │
│  │    (Port 8761)            │  │    (Port 9999)            │                                   │
│  │                          │  │                          │                                    │
│  │  • Service Registry      │  │  • Centralized Config    │                                    │
│  │  • Discovery Client      │  │  • Native (file-based)   │                                    │
│  │  • Health Checks         │  │  • Config Repo:          │                                    │
│  │  • Self-registration     │  │    application.yml       │                                    │
│  │                          │  │    gateway.yml           │                                    │
│  │  All services register   │  │    user-service.yml      │                                    │
│  │  with Eureka on startup  │  │    file-service.yml      │                                    │
│  │                          │  │    payment-service.yml   │                                    │
│  └──────────────────────────┘  │    notification-svc.yml  │                                    │
│                                └──────────────────────────┘                                    │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                            MONITORING STACK                                                     │
│                                                                                                 │
│  ┌──────────────────────────┐  ┌──────────────────────────┐                                    │
│  │    PROMETHEUS             │  │    GRAFANA                │                                   │
│  │    (Port 9090)            │  │    (Port 3000)            │                                   │
│  │                          │  │                          │                                    │
│  │  • Scrapes /actuator/    │  │  • Dashboard:            │                                    │
│  │    prometheus from:      │  │    FileShareX            │                                    │
│  │    - Gateway (8080)      │  │    Microservices         │                                    │
│  │    - User Service (8081) │  │                          │                                    │
│  │    - File Service (8082) │  │  • Panels:               │                                    │
│  │    - Payment Svc (8083)  │  │    - Request Rate        │                                    │
│  │    - Notification (8084) │  │    - Response Time (p95) │                                    │
│  │    - Eureka (8761)       │  │    - Error Rate (%)      │                                    │
│  │                          │  │    - JVM Memory          │                                    │
│  │  • 15s scrape interval   │  │    - Circuit Breaker     │                                    │
│  │  • 15 day retention      │  │    - MongoDB Conns       │                                    │
│  └──────────────────────────┘  └──────────────────────────┘                                    │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                            FRONTEND                                                             │
│                                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────────────────────────┐   │
│  │  React 18 + Vite 5 + Tailwind CSS + Clerk Auth SDK                                     │   │
│  │  (Port 5173 dev / Port 5174 Docker)                                                    │   │
│  │                                                                                         │   │
│  │  Routes:                                                                                │   │
│  │    /                → Landing (public)                                                  │   │
│  │    /dashboard       → Dashboard (auth required)                                         │   │
│  │    /upload          → Upload (auth required)                                            │   │
│  │    /my-files        → MyFiles (auth required)                                           │   │
│  │    /subscriptions   → Subscription (auth required)                                      │   │
│  │    /transactions    → Transactions (auth required)                                      │   │
│  │    /file/:fileId    → PublicFileView (public)                                           │   │
│  │                                                                                         │   │
│  │  All API calls → Gateway (http://localhost:8080/api/v1.0)                              │   │
│  └──────────────────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                            REQUEST FLOW EXAMPLE                                                 │
│                                                                                                 │
│  User uploads file:                                                                             │
│                                                                                                 │
│  1. Frontend (Clerk JWT) → POST /api/v1.0/files/upload → Gateway (8080)                        │
│  2. Gateway validates JWT via Clerk JWKS                                                        │
│  3. Gateway injects X-Clerk-Id header                                                          │
│  4. Gateway routes: /api/v1.0/files/** → lb://file-service (8082)                              │
│  5. StripPrefix=2 removes /api/v1.0                                                            │
│  6. File Service receives /files/upload                                                         │
│  7. File Service calls User Service (REST) to check credits (circuit breaker)                  │
│  8. File Service uploads to Cloudflare R2 (S3 API)                                             │
│  9. File Service saves metadata to MongoDB Files                                               │
│  10. File Service decrements user credits via User Service                                      │
│  11. Response returned to Frontend                                                             │
│                                                                                                 │
│  Prometheus scrapes metrics every 15s → Grafana displays on dashboard                          │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                            DEPENDENCY GRAPH                                                     │
│                                                                                                 │
│                           ┌──────────────┐                                                      │
│                           │   Frontend   │                                                      │
│                           │  (React/Vite)│                                                      │
│                           └──────┬───────┘                                                      │
│                                  │                                                              │
│                                  ▼                                                              │
│                           ┌──────────────┐                                                      │
│                           │   Gateway    │◄── Clerk (JWT)                                       │
│                           │   (8080)     │                                                      │
│                           └──────┬───────┘                                                      │
│                                  │                                                              │
│              ┌───────────────────┼───────────────────┐                                          │
│              ▼                   ▼                   ▼                                          │
│       ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                 │
│       │    User     │    │    File     │    │   Payment   │    │Notification │                 │
│       │   Service   │◄───│   Service   │    │   Service   │    │   Service   │                 │
│       │   (8081)    │    │   (8082)    │◄───│   (8083)    │    │   (8084)    │                 │
│       └──────┬──────┘    └──────┬──────┘    └──────┬──────┘    └──────┬──────┘                 │
│              │                  │                  │                  │                         │
│              ▼                  ▼                  ▼                  ▼                         │
│       ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                 │
│       │   MongoDB   │    │   MongoDB   │    │   MongoDB   │    │   MongoDB   │                 │
│       │   Users     │    │   Files     │    │  Payments   │    │  Notifs     │                 │
│       │  (27017)    │    │  (27018)    │    │  (27019)    │    │  (27020)    │                 │
│       └─────────────┘    └──────┬──────┘    └─────────────┘    └─────────────┘                 │
│                                 │                                                               │
│                                 ▼                                                               │
│                          ┌─────────────┐                                                        │
│                          │ Cloudflare  │                                                        │
│                          │     R2      │                                                        │
│                          └─────────────┘                                                        │
│                                                                                                 │
│  ◄─── REST (direct)   ◄─── Circuit Breaker   ───► Eureka Discovery                            │
│  ───► Config Server (all services import config)                                               │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                            TECHNOLOGY STACK                                                     │
│                                                                                                 │
│  Backend:     Java 21 | Spring Boot 3.4.5 | Spring Cloud 2024.0.1 | Gradle 8.9                │
│  Gateway:     Spring Cloud Gateway (WebFlux/Netty) | Spring Security | JJWT                   │
│  Database:    MongoDB 7 (4 instances, database-per-service pattern)                             │
│  Storage:     Cloudflare R2 (S3-compatible) | AWS SDK v2                                       │
│  Auth:        Clerk (JWT + Webhooks) | React Clerk SDK                                         │
│  Payments:    Razorpay (Orders + HMAC-SHA256 verification)                                     │
│  Frontend:    React 18 | Vite 5 | Tailwind CSS 4 | Axios | React Router DOM 7                 │
│  Monitoring:  Prometheus | Grafana | Micrometer + Brave Tracing                                │
│  Resilience:  Resilience4j Circuit Breaker (file-service, payment-service)                     │
│  Logging:     Logback + Logstash Encoder (structured JSON logs with trace IDs)                 │
│  Container:   Docker | Docker Compose | Nginx (frontend) | Eclipse Temurin 21 (backend)        │
│  CI/CD:       Render (legacy monolith) | Docker Compose (microservices)                        │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Port Mapping Summary

| Service | Host Port | Container Port | Protocol |
|---------|-----------|----------------|----------|
| Frontend | 5173/5174 | 80 (Nginx) | HTTP |
| Gateway | 8080 | 8080 | HTTP |
| User Service | 8081 | 8081 | HTTP |
| File Service | 8082 | 8082 | HTTP |
| Payment Service | 8083 | 8083 | HTTP |
| Notification Service | 8084 | 8084 | HTTP |
| Eureka Server | 8761 | 8761 | HTTP |
| Config Server | 9999 | 9999 | HTTP |
| Prometheus | 9090 | 9090 | HTTP |
| Grafana | 3000 | 3000 | HTTP |
| MongoDB Users | 27017 | 27017 | TCP |
| MongoDB Files | 27018 | 27017 | TCP |
| MongoDB Payments | 27019 | 27017 | TCP |
| MongoDB Notifications | 27020 | 27017 | TCP |

---

## API Route Table

| Gateway Path | Target Service | Context Path | Methods |
|--------------|----------------|--------------|---------|
| `/api/v1.0/users/**` | User Service (8081) | `/api/v1.0` | GET, POST, PATCH |
| `/api/v1.0/webhooks/**` | User Service (8081) | `/api/v1.0` | POST |
| `/api/v1.0/files/**` | File Service (8082) | `/api/v1.0` | GET, POST, PATCH, DELETE |
| `/api/v1.0/payments/**` | Payment Service (8083) | `/api/v1.0` | POST |
| `/api/v1.0/transactions/**` | Payment Service (8083) | `/api/v1.0` | GET |
| `/api/v1.0/notifications/**` | Notification Service (8084) | `/api/v1.0` | GET |
| `/health` | Gateway (direct) | - | GET |
| `/actuator/**` | All services | `/actuator` | GET |

---

## Security Flow

```
┌──────────┐         ┌──────────┐         ┌──────────┐         ┌──────────┐
│  Browser │         │  Clerk   │         │ Gateway  │         │ Backend  │
│          │         │  Cloud   │         │  :8080   │         │  Service │
└────┬─────┘         └────┬─────┘         └────┬─────┘         └────┬─────┘
     │                    │                    │                    │
     │  1. Login          │                    │                    │
     │ ──────────────────►│                    │                    │
     │                    │                    │                    │
     │  2. JWT Token      │                    │                    │
     │ ◄──────────────────│                    │                    │
     │                    │                    │                    │
     │  3. API Request + Bearer Token         │                    │
     │ ──────────────────────────────────────►│                    │
     │                    │                    │                    │
     │                    │  4. Fetch JWKS     │                    │
     │                    │ ◄─────────────────│                    │
     │                    │                    │                    │
     │                    │  5. Public Keys    │                    │
     │                    │ ──────────────────►│                    │
     │                    │                    │                    │
     │                    │  6. Validate JWT   │                    │
     │                    │                    │                    │
     │                    │  7. Extract clerkId │                    │
     │                    │     Inject X-Clerk-Id header           │
     │                    │                    │                    │
     │                    │                    │  8. Forward Request│
     │                    │                    │  + X-Clerk-Id     │
     │                    │                    │ ──────────────────►│
     │                    │                    │                    │
     │                    │                    │  9. Response       │
     │                    │                    │ ◄──────────────────│
     │                    │                    │                    │
     │  10. Response      │                    │                    │
     │ ◄──────────────────────────────────────│                    │
```

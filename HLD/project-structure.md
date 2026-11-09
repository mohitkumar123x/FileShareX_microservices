# FileShareX — Complete Project Structure Reference

> Every folder and file in the root directory, its purpose, and how it fits into the microservices architecture.

---

## Root Directory Layout

```
fileshare_x_microservice/
├── .agents/                    # OpenCode AI agent skills
├── .dockerignore               # Docker build exclusions
├── .DS_Store                   # macOS metadata (safe to ignore)
├── .env                        # Root environment variables
├── .git/                       # Git repository data
├── .gitignore                  # Git tracking exclusions
├── .gradle/                    # Gradle build cache (auto-generated)
├── .vscode/                    # VS Code workspace settings
├── build.gradle                # Root Gradle build config
├── config-repo/                # Centralized configuration files
├── config-server/              # Spring Cloud Config Server
├── docker-compose.yml          # Full Docker orchestration
├── docs/                       # Developer documentation
├── eureka-server/              # Service Discovery (Eureka)
├── file-service/               # File upload/download microservice
├── filesharex_api/             # (REMOVED — legacy monolith artifact)
├── frontend/                   # React SPA
├── gateway/                    # API Gateway (Spring Cloud Gateway)
├── gradle/                     # Gradle wrapper files
├── gradlew                     # Gradle wrapper (Linux/Mac)
├── gradlew.bat                 # Gradle wrapper (Windows)
├── HLD/                        # High-Level Design documents
├── LLD/                        # Low-Level Design documents
├── logs/                       # Runtime log files
├── manage-project.ps1          # Single-command start/stop/status
├── monitoring/                 # Prometheus + Grafana configs
├── notification-service/       # Email notification microservice
├── payment-service/            # Payment processing microservice
├── README.md                   # Project README
├── settings.gradle             # Gradle multi-module settings
├── start-services.ps1          # Legacy startup script (Java services only)
└── user-service/               # User management microservice
```

---

## Root Files — Detailed Reference

### `.dockerignore`
**Purpose:** Excludes files from Docker build context to speed up builds and prevent secrets from leaking into images.

**What it excludes:**
| Pattern | Reason |
|---------|--------|
| `Thumbs.db`, `.DS_Store` | OS metadata |
| `.idea`, `.vscode`, `*.swp` | IDE files |
| `.git`, `.gitignore` | Git data (not needed in container) |
| `docs`, `HLD`, `LLD`, `*.md` | Documentation (not needed in runtime) |
| `node_modules` | NPM dependencies (rebuilt in container) |
| `uploads`, `postgres_*_data` | Runtime data |
| `*.log` | Log files |

---

### `.env`
**Purpose:** Stores environment variables needed by the frontend and Docker Compose. Used as build-time and runtime config.

**Contents:**
```env
VITE_CLERK_PUBLISHABLE_KEY=pk_test_...   # Clerk auth (frontend)
VITE_RAZORPAY_KEY=rzp_test_...           # Razorpay payment (frontend)
VITE_API_BASE_URL=http://localhost:8080/api/v1.0  # Gateway URL
```

**Security:** This file is in `.gitignore` and never committed to Git. Contains API keys.

---

### `.gitignore`
**Purpose:** Tells Git which files to never track or commit.

**What it excludes:**
| Pattern | Reason |
|---------|--------|
| `.env`, `.env.*` | Secrets and environment config |
| `*.log` | Runtime logs |
| `uploads_data/`, `postgres_*_data/` | Persistent data volumes |
| IDE files | `.idea`, `.vscode`, swap files |

---

### `build.gradle`
**Purpose:** Root Gradle build configuration for all 7 microservices. Defines shared plugins, dependencies, and Java version.

**Key settings:**
| Setting | Value |
|---------|-------|
| Spring Boot | 3.4.5 |
| Spring Cloud | 2024.0.1 |
| Java Version | 21 (source + target) |
| Lombok | All subprojects |
| Logstash Encoder | All subprojects (structured logging) |

**Applies to all subprojects:**
- `java` plugin
- `org.springframework.boot` plugin
- `io.spring.dependency-management` plugin
- Spring Cloud BOM import

---

### `settings.gradle`
**Purpose:** Defines the multi-module Gradle project. Lists all 7 microservices that can be built together.

**Modules:**
```
eureka-server, config-server, gateway,
user-service, file-service, payment-service, notification-service
```

**Root project name:** `filesharex`

---

### `docker-compose.yml`
**Purpose:** Full Docker Compose definition for the entire microservices stack. Orchestrates 15+ containers.

**Services defined:**

| Category | Service | Port | Image |
|----------|---------|------|-------|
| **Infrastructure** | eureka-server | 8761 | Custom Dockerfile |
| | config-server | 9999 | Custom Dockerfile |
| **Gateway** | gateway | 8080 | Custom Dockerfile |
| **Business** | user-service | 8081 | Custom Dockerfile |
| | file-service | 8082 | Custom Dockerfile |
| | payment-service | 8083 | Custom Dockerfile |
| | notification-service | 8084 | Custom Dockerfile |
| **Databases** | postgres-users | 5433→5432 | postgres:17-alpine |
| | postgres-files | 5434→5432 | postgres:17-alpine |
| | postgres-payments | 5435→5432 | postgres:17-alpine |
| | postgres-notifications | 5436→5432 | postgres:17-alpine |
| **Monitoring** | prometheus | 9090 | prom/prometheus:latest |
| | grafana | 3000 | grafana/grafana:latest |
| **Frontend** | frontend | 5174 | Custom Dockerfile (Nginx) |

**Named volumes:** `postgres_users_data`, `postgres_files_data`, `postgres_payments_data`, `postgres_notifications_data`, `prometheus_data`, `grafana_data`

---

### `gradlew` / `gradlew.bat`
**Purpose:** Gradle wrapper scripts. Automatically downloads the correct Gradle version so developers don't need to install Gradle manually.

- `gradlew` — for Linux/Mac
- `gradlew.bat` — for Windows

---

### `gradle/wrapper/`
**Purpose:** Contains the Gradle wrapper JAR and properties. Ensures consistent Gradle version across all developer machines.

**Files:**
- `gradle-wrapper.jar` — Bootstrap JAR that downloads Gradle
- `gradle-wrapper.properties` — Specifies Gradle version URL

---

### `manage-project.ps1`
**Purpose:** **Single-command manager** for the entire project. Start, stop, or check status of all services.

**Commands:**
```powershell
.\manage-project.ps1 start    # Start everything
.\manage-project.ps1 stop     # Stop everything
.\manage-project.ps1 status   # Check all services
```

**What `start` does (in order):**
1. Starts Docker PostgreSQL containers
2. Starts Prometheus + Grafana containers
3. Starts all 7 Java services (Eureka → Config → Gateway → Business)
4. Starts React frontend via npm
5. Runs health checks and shows status

**What `stop` does:**
- Kills all Java processes on service ports (8761, 9999, 8080-8084)
- Kills frontend process on port 5173

**What `status` does:**
- Health checks all 7 Java services
- Checks Docker container status
- Checks frontend availability
- Shows all service URLs

---

### `start-services.ps1`
**Purpose:** Legacy startup script (superseded by `manage-project.ps1`). Starts only the Java services.

**Limitations compared to `manage-project.ps1`:**
- No Docker container management
- No Prometheus/Grafana startup
- No frontend startup
- Has infinite monitoring loop (`while ($true)`) that never exits
- Uses hardcoded Java path (`C:\Users\mohit\.antigravity-ide\...`)

**Recommendation:** Use `manage-project.ps1` instead.

---

### `README.md`
**Purpose:** Project overview and quick-start guide. Documents both the legacy monolith and current microservices setup.

**Sections:**
- Tech Stack (Backend + Frontend)
- Project Structure
- Backend/Frontend Configuration
- Main API Endpoints
- Run Locally instructions
- Docker instructions
- Deploy on Render (legacy)

**Note:** Still references the old `backend/` directory. Needs update for microservices.

---

## Root Folders — Detailed Reference

### `.agents/`
**Purpose:** OpenCode AI agent configuration and skills.

**Contents:**
```
.agents/
└── skills/
    ├── java-microservices/    # Microservices architecture skill
    ├── java-pro/              # Java 21+ best practices skill
    └── java-springboot/       # Spring Boot skill
```

**Used by:** OpenCode AI assistant for context-aware code generation.

---

### `.vscode/`
**Purpose:** VS Code workspace settings specific to this project.

**Files:**
- `settings.json` — Editor preferences (format on save, Java config, etc.)

---

### `config-repo/`
**Purpose:** **Centralized configuration repository** for all microservices. Spring Cloud Config Server reads from this folder.

**Files:**
| File | Purpose |
|------|---------|
| `application.yml` | Shared config for ALL services (clerk, razorpay, actuator, logging) |
| `gateway.yml` | Gateway-specific routes and CORS config |
| `user-service.yml` | User service PostgreSQL (datasource/JPA) + Clerk webhook config |
| `file-service.yml` | File service PostgreSQL (datasource/JPA) + R2 storage config |
| `payment-service.yml` | Payment service PostgreSQL (datasource/JPA) + Razorpay config |
| `notification-service.yml` | Notification service PostgreSQL (datasource/JPA) + SMTP config |

**How it works:**
1. Config Server starts and reads `config-repo/` folder
2. Each service fetches its config from Config Server on startup
3. Example: File Service gets `file-service.yml` + shared `application.yml`

---

### `config-server/`
**Purpose:** **Spring Cloud Config Server** — centralizes configuration for all microservices.

**Port:** 9999

**Technology:** Spring Cloud Config Server

**Folder structure:**
```
config-server/
├── build.gradle        # Dependencies (spring-cloud-config-server)
├── Dockerfile          # Container build
├── src/                # Java source code
│   └── main/
│       ├── java/       # ConfigServerApplication.java
│       └── resources/
│           └── application.yml  # Points to config-repo/
└── bin/                # Compiled classes
```

**Responsibilities:**
- Serves configuration files to all services
- Supports `/actuator/health` for health checks
- Known limitation: intercepts `/actuator/{profile}` treating "prometheus" as profile

---

### `eureka-server/`
**Purpose:** **Service Discovery** — all microservices register here and find each other.

**Port:** 8761

**Technology:** Spring Cloud Netflix Eureka Server

**Folder structure:**
```
eureka-server/
├── build.gradle        # Dependencies (spring-cloud-starter-netflix-eureka-server)
├── Dockerfile          # Container build
├── src/                # Java source code
│   └── main/
│       ├── java/       # EurekaServerApplication.java
│       └── resources/
│           └── application.yml  # Eureka config + actuator exposure
├── bin/                # Compiled classes
└── build/              # Built JAR
```

**Responsibilities:**
- Service registry (all services register with `spring.application.name`)
- Health monitoring (tracks which services are UP/DOWN)
- Load balancing support (services discover each other by name)

---

### `gateway/`
**Purpose:** **API Gateway** — single entry point for all client requests. Routes, authentication, and CORS.

**Port:** 8080

**Technology:** Spring Cloud Gateway (WebFlux/Netty — NOT Tomcat)

**Folder structure:**
```
gateway/
├── build.gradle        # Dependencies (spring-cloud-starter-gateway, security)
├── Dockerfile          # Container build
├── src/
│   └── main/
│       ├── java/
│       │   └── filesharex/gateway/
│       │       ├── GatewayApplication.java
│       │       ├── config/
│       │       │   └── SecurityConfig.java    # CORS + JWT config
│       │       └── filter/
│       │           └── ClerkJwtAuthFilter.java # JWT validation
│       └── resources/
│           └── application.yml  # Routes + CORS + actuator
├── bin/
└── build/
```

**Responsibilities:**
- Route matching (`/api/v1.0/users/**` → User Service)
- JWT authentication (Clerk JWKS validation)
- CORS configuration
- StripPrefix=2 (removes `/api/v1.0` before forwarding)
- Circuit breaker integration (Resilience4j)
- `/actuator/**` endpoints exposed for monitoring

---

### `user-service/`
**Purpose:** **User Management** — handles profiles, credits, and Clerk webhook events.

**Port:** 8081

**Context Path:** `/api/v1.0`

**Folder structure:**
```
user-service/
├── build.gradle        # Dependencies (web, JPA/PostgreSQL, security, resilience4j)
├── Dockerfile          # Container build
├── src/
│   └── main/
│       ├── java/
│       │   └── filesharex/userservice/
│       │       ├── UserServiceApplication.java
│       │       ├── controller/       # REST controllers
│       │       ├── service/          # Business logic
│       │       ├── repository/       # JPA repositories
│       │       ├── model/            # Entity + DTO classes
│       │       ├── config/           # Security + Mapper config
│       │       └── exception/        # Global exception handler
│       └── resources/
│           └── application.yml
├── bin/
└── build/
```

**Responsibilities:**
- User profile CRUD (created via Clerk webhooks)
- Credit management (balance, add, deduct)
- Webhook handling (Clerk user lifecycle events)
- User validation (called by File + Payment services via REST)

**Database:** `filesharex_users` (PostgreSQL 17, host port 5433 → container 5432)
**Tables:** `profiles`, `user_credits`

---

### `file-service/`
**Purpose:** **File Operations** — handles upload, download, metadata, and Cloudflare R2 storage.

**Port:** 8082

**Context Path:** `/api/v1.0`

**Folder structure:**
```
file-service/
├── build.gradle        # Dependencies (web, JPA/PostgreSQL, AWS SDK, resilience4j)
├── Dockerfile          # Container build
├── src/
│   └── main/
│       ├── java/
│       │   └── filesharex/fileservice/
│       │       ├── FileServiceApplication.java
│       │       ├── controller/       # REST controllers
│       │       ├── service/          # Business logic
│       │       ├── repository/       # JPA repositories
│       │       ├── model/            # Entity + DTO classes
│       │       ├── config/           # R2 + Mapper config
│       │       ├── client/           # UserClient (REST calls to User Service)
│       │       └── exception/        # Global exception handler
│       └── resources/
│           └── application.yml
├── bin/
└── build/
```

**Responsibilities:**
- File upload to Cloudflare R2 (S3-compatible)
- File download via R2 public URLs
- File metadata CRUD (PostgreSQL via Spring Data JPA)
- Public/Private file toggle
- Credits check before upload (calls User Service via REST)
- Circuit breaker on User Service calls

**Database:** `filesharex_files` (PostgreSQL 17, host port 5434 → container 5432)
**Tables:** `files`

---

### `payment-service/`
**Purpose:** **Payment Processing** — handles Razorpay orders, verification, and credit allocation.

**Port:** 8083

**Context Path:** `/api/v1.0`

**Folder structure:**
```
payment-service/
├── build.gradle        # Dependencies (web, JPA/PostgreSQL, Razorpay SDK)
├── Dockerfile          # Container build
├── src/
│   └── main/
│       ├── java/
│       │   └── filesharex/paymentservice/
│       │       ├── PaymentServiceApplication.java
│       │       ├── controller/       # REST controllers
│       │       ├── service/          # Business logic
│       │       ├── repository/       # JPA repositories
│       │       ├── model/            # Entity + DTO classes
│       │       ├── client/           # UserClient (REST calls to User Service)
│       │       └── exception/        # Global exception handler
│       └── resources/
│           └── application.yml
├── bin/
└── build/
```

**Responsibilities:**
- Create Razorpay orders (`POST /payments/create-order`)
- Verify payment signatures (HMAC-SHA256)
- Allocate credits to user on successful payment
- Transaction history (PostgreSQL via Spring Data JPA)
- Circuit breaker on User Service calls

**Database:** `filesharex_payments` (PostgreSQL 17, host port 5435 → container 5432)
**Tables:** `payment_transactions`

---

### `notification-service/`
**Purpose:** **Email Notifications** — sends transactional emails via SMTP/Gmail.

**Port:** 8084

**Context Path:** `/api/v1.0`

**Folder structure:**
```
notification-service/
├── build.gradle        # Dependencies (web, JPA/PostgreSQL, spring-boot-starter-mail)
├── Dockerfile          # Container build
├── src/
│   └── main/
│       ├── java/
│       │   └── filesharex/notificationservice/
│       │       ├── NotificationServiceApplication.java
│       │       ├── controller/       # REST controllers
│       │       ├── service/          # Email sending logic
│       │       ├── repository/       # JPA repositories
│       │       └── model/            # Entity + DTO classes
│       └── resources/
│           └── application.yml
├── bin/
└── build/
```

**Responsibilities:**
- Send file sharing notifications
- Email template management
- Notification history (PostgreSQL via Spring Data JPA)

**Database:** `filesharex_notifications` (PostgreSQL 17, host port 5436 → container 5432)
**Tables:** `notifications`

---

### `frontend/`
**Purpose:** **React Single-Page Application** — the user-facing web interface.

**Port:** 5173 (dev) / 5174 (Docker/Nginx)

**Technology:** React 18 + Vite + Tailwind CSS + Clerk

**Folder structure:**
```
frontend/
├── .dockerignore           # Docker build exclusions
├── .env                    # Frontend env vars (Clerk, Razorpay, API URL)
├── .eslintrc.cjs           # ESLint config
├── .gitignore              # Git exclusions
├── Dockerfile              # Multi-stage build (Node → Nginx)
├── index.html              # HTML entry point
├── nginx.conf              # Nginx config for SPA routing
├── package.json            # NPM dependencies
├── package-lock.json       # Locked dependency versions
├── vite.config.js          # Vite build config
├── public/                 # Static assets (favicon, etc.)
├── src/                    # React source code
│   ├── App.jsx             # Main app + routing
│   ├── main.jsx            # Entry point
│   ├── pages/              # Page components
│   ├── components/         # Reusable UI components
│   └── util/               # API endpoints, helpers
├── dist/                   # Built production files
└── node_modules/           # Installed dependencies
```

**Routes:**
| Route | Component | Auth |
|-------|-----------|------|
| `/` | Landing | No |
| `/dashboard` | Dashboard | Yes |
| `/upload` | Upload | Yes |
| `/my-files` | MyFiles | Yes |
| `/subscriptions` | Subscription | Yes |
| `/transactions` | Transactions | Yes |
| `/file/:fileId` | PublicFileView | No |

---

### `monitoring/`
**Purpose:** **Prometheus + Grafana configuration** for metrics collection and visualization.

**Folder structure:**
```
monitoring/
├── prometheus/
│   └── prometheus.yml              # Scrape targets config
└── grafana/
    ├── provisioning/
    │   ├── dashboards/
    │   │   └── dashboards.yml      # Dashboard provisioning config
    │   └── datasources/
    │       └── prometheus.yml       # Prometheus datasource config
    └── dashboards/
        └── filesharex-dashboard.json # Pre-built Grafana dashboard
```

**Prometheus targets (`prometheus.yml`):**
| Job | Target | Metrics Path |
|-----|--------|--------------|
| filesharex-gateway | host.docker.internal:8080 | /actuator/prometheus |
| filesharex-user-service | host.docker.internal:8081 | /api/v1.0/actuator/prometheus |
| filesharex-file-service | host.docker.internal:8082 | /api/v1.0/actuator/prometheus |
| filesharex-payment-service | host.docker.internal:8083 | /api/v1.0/actuator/prometheus |
| filesharex-notification-service | host.docker.internal:8084 | /api/v1.0/actuator/prometheus |
| eureka-server | host.docker.internal:8761 | /actuator/prometheus |

**Note:** Config Server excluded — Spring Cloud intercepts `/actuator/prometheus` as a profile lookup.

---

### `docs/`
**Purpose:** **Developer documentation** — guides, fixes, and operational notes.

**Files:**
| File | Purpose |
|------|---------|
| `01-file-upload-fix.md` | Fix for file upload issues |
| `02-docker-file-persistence.md` | Docker volume persistence setup |
| `03-clerk-configuration.md` | Clerk auth setup guide |
| `04-profile-auto-creation.md` | Auto-create user profiles on signup |
| `05-payment-mock-mode.md` | Razorpay mock mode for testing |
| `06-spa-routing-fix.md` | SPA routing fix for Nginx |
| `07-monitoring-setup.md` | Prometheus + Grafana setup |
| `08-architecture-diagram.md` | ASCII architecture diagrams |
| `ERRORS-AND-FIXES.md` | All errors encountered and fixes |
| `PHASE-1-MVP-Cloud-Drive.docx` | Phase 1 design document |
| `PHASE-1.md` | Phase 1 notes |
| `README.md` | Docs folder index |

---

### `HLD/`
**Purpose:** **High-Level Design** documents — architecture, deployment, and system design.

**Files:**
| File | Purpose |
|------|---------|
| `README.md` | Complete HLD (architecture, services, security, monitoring) |
| `deployment-architecture.md` | Docker Compose topology, Render blueprint |
| `architecture-diagram.md` | ASCII architecture diagrams |
| `project-structure.md` | **THIS FILE** — complete folder/file reference |

---

### `LLD/`
**Purpose:** **Low-Level Design** documents — code patterns, database schemas, API contracts.

**Files:**
| File | Purpose |
|------|---------|
| `design-patterns.md` | 18 design patterns used across services |
| `database-design.md` | PostgreSQL table schemas |
| `api-design.md` | REST API contracts |
| `class-diagrams.md` | UML class diagrams |

---

### `logs/`
**Purpose:** **Runtime log files** — stdout/stderr from all Java services.

**Files (auto-generated):**
| File | Source |
|------|--------|
| `eureka-stdout.log` | Eureka Server |
| `config-stdout.log` | Config Server |
| `gateway-stdout.log` | Gateway |
| `user-stdout.log` | User Service |
| `file-stdout.log` | File Service |
| `payment-stdout.log` | Payment Service |
| `notification-stdout.log` | Notification Service |

**Note:** Created by `start-services.ps1`. Files listed in `.gitignore`.

---

### `.gradle/`
**Purpose:** Gradle build cache. Auto-generated by Gradle to speed up subsequent builds.

**Note:** Safe to delete (will be recreated). Listed in `.gitignore` (not tracked).

---

### `build/`
**Purpose:** Root-level Gradle build output. Contains compiled classes and build artifacts.

**Note:** Auto-generated. Listed in `.gitignore`.

---

## Service Folder Pattern (Shared Across All 7 Services)

Each microservice follows the same folder structure:

```
<service-name>/
├── build.gradle        # Service-specific dependencies
├── Dockerfile          # Container build instructions
├── src/
│   └── main/
│       ├── java/
│       │   └── filesharex/<service>/
│       │       ├── <Service>Application.java    # Main class
│       │       ├── controller/                  # REST endpoints
│       │       ├── service/                     # Business logic
│       │       ├── repository/                  # Data access
│       │       ├── model/                       # Entities + DTOs
│       │       ├── config/                      # Configuration
│       │       ├── client/                      # REST clients (inter-service)
│       │       └── exception/                   # Global exception handler
│       └── resources/
│           └── application.yml                  # Service config
├── bin/                # Compiled classes
└── build/              # Built JAR
```

---

## Port Map

| Port | Service | Protocol |
|------|---------|----------|
| 3000 | Grafana Dashboard | HTTP |
| 5173 | Frontend (dev) | HTTP |
| 5174 | Frontend (Docker/Nginx) | HTTP |
| 5433 | PostgreSQL Users (host → container 5432) | TCP |
| 5434 | PostgreSQL Files (host → container 5432) | TCP |
| 5435 | PostgreSQL Payments (host → container 5432) | TCP |
| 5436 | PostgreSQL Notifications (host → container 5432) | TCP |
| 8080 | API Gateway | HTTP |
| 8081 | User Service | HTTP |
| 8082 | File Service | HTTP |
| 8083 | Payment Service | HTTP |
| 8084 | Notification Service | HTTP |
| 8761 | Eureka Server | HTTP |
| 9090 | Prometheus | HTTP |
| 9999 | Config Server | HTTP |

---

## Quick Commands

```powershell
# Start everything
.\manage-project.ps1 start

# Stop everything
.\manage-project.ps1 stop

# Check status
.\manage-project.ps1 status

# Build all services
.\gradlew.bat :eureka-server:bootJar :config-server:bootJar :gateway:bootJar :user-service:bootJar :file-service:bootJar :payment-service:bootJar :notification-service:bootJar --no-daemon

# Start frontend only
cd frontend; npm run dev
```

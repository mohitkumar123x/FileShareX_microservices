# Design Patterns - FileShareX Microservices

## Overview

This document catalogs all design patterns applied in the FileShareX microservices architecture, organized by pattern category (Creational, Structural, Behavioural).

---

## Creational Patterns

### 1. Factory Method Pattern

**Purpose:** Decouple object creation from usage; allow runtime selection of implementations.

#### FileStorageFactory (File Service)
```
file-service/src/main/java/filesharex/file/storage/
├── FileStorageFactory.java
├── FileStorageStrategy.java (interface)
├── R2FileStorageStrategy.java
└── LocalFileStorageStrategy.java
```

**Implementation:**
```java
@Component
public class FileStorageFactory {
    private final Map<String, FileStorageStrategy> strategies;

    public FileStorageFactory(List<FileStorageStrategy> strategyList) {
        this.strategies = strategyList.stream()
            .collect(Collectors.toMap(FileStorageStrategy::getType, Function.identity()));
    }

    public FileStorageStrategy getStrategy(String type) {
        return Optional.ofNullable(strategies.get(type))
            .orElseThrow(() -> new IllegalArgumentException("Unknown storage type: " + type));
    }
}
```

**Usage:**
```java
FileStorageStrategy strategy = factory.getStrategy("r2"); // or "local"
strategy.upload(key, file);
```

**Benefits:**
- New storage backends added without modifying existing code (Open/Closed Principle)
- Configuration-driven strategy selection (`file.storage.type=r2`)
- Spring auto-discovers all implementations

---

#### PaymentGatewayFactory (Payment Service)
```
payment-service/src/main/java/filesharex/payment/gateway/
├── PaymentGatewayFactory.java
├── PaymentGateway.java (interface)
├── RazorpayGateway.java
└── MockPaymentGateway.java
```

**Implementation:**
```java
@Component
public class PaymentGatewayFactory {
    private final Map<String, PaymentGateway> gateways;

    public PaymentGatewayFactory(List<PaymentGateway> gatewayList) {
        this.gateways = gatewayList.stream()
            .collect(Collectors.toMap(PaymentGateway::getProvider, Function.identity()));
    }

    public PaymentGateway getGateway(String provider) {
        return Optional.ofNullable(gateways.get(provider))
            .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + provider));
    }
}
```

**Benefits:**
- Switch payment providers via config (`payment.provider=razorpay` or `mock`)
- Easy to add new providers (Stripe, PayPal) without changing PaymentService

---

### 2. Builder Pattern

**Purpose:** Construct complex objects with many optional fields; fluent API.

#### PaymentResult (Payment Service)
```java
@Builder
@Getter
public class PaymentResult {
    private boolean success;
    private String orderId;
    private String paymentId;
    private String message;
    private int creditsAdded;
    private String plan;
    private boolean mockMode;
}
```

**Usage:**
```java
return PaymentResult.builder()
    .success(true)
    .orderId(order.get("id"))
    .message("Order created successfully")
    .mockMode(false)
    .build();
```

**Benefits:**
- Readable object construction
- Immutable objects
- No telescoping constructors

---

#### FileMetadataDocument, ProfileDTO, UserCreditsDTO
All use `@Builder` for consistent construction across all services.

---

## Structural Patterns

### 3. Facade Pattern

**Purpose:** Provide a unified interface to a subsystem; hide complexity.

#### FileStorageFacade (File Service)
```
file-service/src/main/java/filesharex/file/storage/
└── FileStorageFacade.java
```

**Implementation:**
```java
@Component
@RequiredArgsConstructor
public class FileStorageFacade {
    private final FileStorageFactory factory;
    @Value("${file.storage.type:r2}")
    private String storageType;

    public String store(MultipartFile file, String clerkId) {
        FileStorageStrategy strategy = factory.getStrategy(storageType);
        String key = buildKey(file, clerkId);
        strategy.upload(key, file);
        return key;
    }

    public InputStream retrieve(String fileLocation) {
        return factory.getStrategy(storageType).download(fileLocation);
    }

    public void remove(String fileLocation) {
        factory.getStrategy(storageType).delete(fileLocation);
    }

    public String getDownloadUrl(String fileLocation) {
        return factory.getStrategy(storageType).getPublicUrl(fileLocation);
    }

    private String buildKey(MultipartFile file, String clerkId) {
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        return String.format("uploads/%s/%s.%s", clerkId, UUID.randomUUID(), ext);
    }
}
```

**Benefits:**
- Single entry point for all storage operations
- Key generation logic centralized
- Service layer doesn't know about R2/Local details
- Easy to add cross-cutting concerns (logging, metrics)

---

### 4. Adapter Pattern

**Purpose:** Convert one interface to another; integrate third-party libraries.

#### RazorpayGateway (Payment Service)
```
payment-service/src/main/java/filesharex/payment/gateway/
└── RazorpayGateway.java
```

**Implementation:**
```java
@Component
public class RazorpayGateway implements PaymentGateway {
    @Value("${razorpay.key.id}")
    private String keyId;
    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Override
    public String getProvider() { return "razorpay"; }

    @Override
    public PaymentResult createOrder(PaymentDTO dto) {
        // Adapts Razorpay SDK to common PaymentGateway interface
        RazorpayClient client = new RazorpayClient(keyId, keySecret);
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", dto.getAmount());
        orderRequest.put("currency", dto.getCurrency());
        Order order = client.orders.create(orderRequest);

        return PaymentResult.builder()
            .orderId(order.get("id"))
            .success(true)
            .build();
    }

    @Override
    public PaymentResult verify(PaymentVerificationDTO dto) {
        // Adapts Razorpay signature verification
        String data = dto.getRazorpay_order_id() + "|" + dto.getRazorpay_payment_id();
        String signature = generateHmacSha256(data, keySecret);
        boolean valid = signature.equals(dto.getRazorpay_signature());
        // ...
    }
}
```

**Benefits:**
- PaymentService uses `PaymentGateway` interface, not Razorpay directly
- MockPaymentGateway for development/testing
- Swap providers without changing business logic

---

## Behavioural Patterns

### 5. Strategy Pattern

**Purpose:** Define a family of algorithms, make them interchangeable.

#### FileStorageStrategy (File Service)
```java
public interface FileStorageStrategy {
    String getType();  // "r2" or "local"
    void upload(String key, MultipartFile file);
    InputStream download(String key);
    void delete(String key);
    String getPublicUrl(String key);
}
```

**Implementations:**
- `R2FileStorageStrategy` → Cloudflare R2 via S3 SDK
- `LocalFileStorageStrategy` → Local filesystem

**Selection:** `file.storage.type=r2` (config-driven)

---

#### PaymentGateway (Payment Service)
```java
public interface PaymentGateway {
    String getProvider();  // "razorpay" or "mock"
    PaymentResult createOrder(PaymentDTO dto);
    PaymentResult verify(PaymentVerificationDTO dto);
}
```

**Implementations:**
- `RazorpayGateway` → Production Razorpay integration
- `MockPaymentGateway` → Development/testing

**Selection:** `payment.provider=razorpay` (config-driven)

---

### 6. Chain of Responsibility Pattern

**Purpose:** Pass request through a chain of handlers; each decides to process or pass.

#### File Validation Chain (File Service)
```
file-service/src/main/java/filesharex/file/validation/
├── FileValidationRule.java (abstract handler)
├── CreditValidationRule.java
├── FileSizeValidationRule.java
├── FileTypeValidationRule.java
└── FileValidationChain.java (chain builder)
```

**Abstract Handler:**
```java
public abstract class FileValidationRule {
    private FileValidationRule next;

    public FileValidationRule chain(FileValidationRule next) {
        this.next = next;
        return next;
    }

    public void validate(MultipartFile file, String clerkId) {
        doValidate(file, clerkId);
        if (next != null) {
            next.validate(file, clerkId);
        }
    }

    protected abstract void doValidate(MultipartFile file, String clerkId);
}
```

**Concrete Rules:**
```java
@Component
public class CreditValidationRule extends FileValidationRule {
    private final UserClient userClient;

    @Override
    protected void doValidate(MultipartFile file, String clerkId) {
        int credits = userClient.getCredits(clerkId);
        if (credits <= 0) {
            throw new RuntimeException("Not enough credits");
        }
    }
}

@Component
public class FileSizeValidationRule extends FileValidationRule {
    @Value("${file.validation.max-size:5242880}")
    private long maxSize;

    @Override
    protected void doValidate(MultipartFile file, String clerkId) {
        if (file.getSize() > maxSize) {
            throw new RuntimeException("File size exceeds maximum");
        }
    }
}

@Component
public class FileTypeValidationRule extends FileValidationRule {
    @Value("${file.validation.allowed-types:...}")
    private String allowedTypes;

    @Override
    protected void doValidate(MultipartFile file, String clerkId) {
        // Check MIME type
    }
}
```

**Chain Builder:**
```java
@Component
public class FileValidationChain {
    private final FileValidationRule chain;

    public FileValidationChain(CreditValidationRule credit,
                               FileSizeValidationRule size,
                               FileTypeValidationRule type) {
        credit.chain(size).chain(type);  // Credit → Size → Type
        this.chain = credit;
    }

    public void validate(MultipartFile file, String clerkId) {
        chain.validate(file, clerkId);
    }
}
```

**Flow:**
```
File Upload → Credit Check → Size Check → Type Check → Upload
```

**Benefits:**
- Rules are independent and composable
- Easy to add/remove/reorder validation rules
- Single Responsibility: each rule does one thing

---

### 7. Template Method Pattern

**Purpose:** Define skeleton of algorithm; let subclasses override specific steps.

#### File Processing Pipeline (File Service)
```
file-service/src/main/java/filesharex/file/processing/
├── AbstractFileProcessingPipeline.java
├── StandardUploadPipeline.java
└── PublicSharePipeline.java
```

**Abstract Template:**
```java
public abstract class AbstractFileProcessingPipeline {

    protected final FileStorageFacade storageFacade;

    public final FileMetadataDocument execute(MultipartFile file, String clerkId) {
        preProcess(file, clerkId);                    // Step 1: hook
        String key = storeFile(file, clerkId);         // Step 2: hook
        FileMetadataDocument doc = buildMetadata(file, clerkId, key);  // Step 3: hook
        postProcess(doc);                              // Step 4: hook
        return doc;
    }

    protected abstract void preProcess(MultipartFile file, String clerkId);
    protected abstract String storeFile(MultipartFile file, String clerkId);

    protected FileMetadataDocument buildMetadata(MultipartFile file, String clerkId, String key) {
        return FileMetadataDocument.builder()
            .fileLocation(key)
            .name(file.getOriginalFilename())
            .size(file.getSize())
            .type(file.getContentType())
            .clerkId(clerkId)
            .isPublic(false)
            .uploadedAt(LocalDateTime.now())
            .build();
    }

    protected abstract void postProcess(FileMetadataDocument doc);
}
```

**Concrete Pipelines:**
```java
@Component
public class StandardUploadPipeline extends AbstractFileProcessingPipeline {
    private final FileValidationChain validationChain;
    private final UserClient userClient;

    @Override
    protected void preProcess(MultipartFile file, String clerkId) {
        validationChain.validate(file, clerkId);  // Run validation chain
    }

    @Override
    protected String storeFile(MultipartFile file, String clerkId) {
        return storageFacade.store(file, clerkId);  // Store via facade
    }

    @Override
    protected void postProcess(FileMetadataDocument doc) {
        userClient.consumeCredit(doc.getClerkId());  // Consume credit
    }
}

@Component
public class PublicSharePipeline extends AbstractFileProcessingPipeline {
    @Override
    protected void preProcess(MultipartFile file, String clerkId) {
        // No validation for public share
    }

    @Override
    protected String storeFile(MultipartFile file, String clerkId) {
        return storageFacade.store(file, clerkId);
    }

    @Override
    protected FileMetadataDocument buildMetadata(MultipartFile file, String clerkId, String key) {
        FileMetadataDocument doc = super.buildMetadata(file, clerkId, key);
        doc.setIsPublic(true);  // Override: set public
        return doc;
    }

    @Override
    protected void postProcess(FileMetadataDocument doc) {
        // No credit consumption for public share
    }
}
```

**Benefits:**
- Algorithm structure fixed, steps customizable
- Code reuse across pipelines
- Easy to add new upload workflows

---

### 8. Observer Pattern (via Spring Events)

**Purpose:** Decouple event producer from consumers; notify multiple listeners.

#### Credit Events (User Service)
```
user-service/src/main/java/filesharex/user/event/
├── CreditConsumedEvent.java
├── CreditsAddedEvent.java
└── UserEventListener.java
```

**Events:**
```java
public class CreditConsumedEvent extends ApplicationEvent {
    private final String clerkId;
    private final int creditsRemaining;

    public CreditConsumedEvent(Object source, String clerkId, int creditsRemaining) {
        super(source);
        this.clerkId = clerkId;
        this.creditsRemaining = creditsRemaining;
    }
}

public class CreditsAddedEvent extends ApplicationEvent {
    private final String clerkId;
    private final int creditsAdded;
    private final String plan;
    // constructor, getters
}
```

**Listener:**
```java
@Component
@Slf4j
public class UserEventListener {

    @EventListener
    public void handleCreditConsumed(CreditConsumedEvent event) {
        log.info("Credits consumed for user {}: {} remaining",
            event.getClerkId(), event.getCreditsRemaining());

        if (event.getCreditsRemaining() <= 2) {
            log.warn("Low credits warning for user {}", event.getClerkId());
            // Could trigger notification
        }
    }

    @EventListener
    public void handleCreditsAdded(CreditsAddedEvent event) {
        log.info("Credits added for user {}: +{}, plan={}",
            event.getClerkId(), event.getCreditsAdded(), event.getPlan());
    }
}
```

**Publisher:**
```java
@Service
public class UserCreditsService {
    private final ApplicationEventPublisher eventPublisher;

    public UserCredits consumeCredit(String clerkId) {
        UserCredits saved = userCreditsRepository.save(userCredits);
        eventPublisher.publishEvent(new CreditConsumedEvent(this, clerkId, saved.getCredits()));
        return saved;
    }
}
```

**Benefits:**
- CreditService doesn't know about listeners
- Easy to add new consumers (notifications, analytics, etc.)
- Async support available via `@Async`

---

### 9. Repository Pattern

**Purpose:** Abstract data access layer; decouple business logic from persistence.

#### MongoDB Repositories (All Services)
```
user-service/src/main/java/filesharex/user/repository/
├── ProfileRepository.java
└── UserCreditsRepository.java

file-service/src/main/java/filesharex/file/repository/
└── FileMetadataRepository.java

payment-service/src/main/java/filesharex/payment/repository/
└── PaymentTransactionRepository.java

notification-service/src/main/java/filesharex/notification/repository/
└── NotificationRepository.java
```

**Implementation:**
```java
// file-service
public interface FileMetadataRepository extends MongoRepository<FileMetadataDocument, String> {
    List<FileMetadataDocument> findByClerkId(String clerkId);
    Optional<FileMetadataDocument> findByIdAndClerkId(String id, String clerkId);
    long countByClerkId(String clerkId);
}

// user-service
public interface UserCreditsRepository extends MongoRepository<UserCredits, String> {
    Optional<UserCredits> findByClerkId(String clerkId);
}

public interface ProfileRepository extends MongoRepository<ProfileDocument, String> {
    Optional<ProfileDocument> findByClerkId(String clerkId);
    boolean existsByClerkId(String clerkId);
}

// payment-service
public interface PaymentTransactionRepository extends MongoRepository<PaymentTransaction, String> {
    List<PaymentTransaction> findByClerkId(String clerkId);
    Optional<PaymentTransaction> findByOrderId(String orderId);
}

// notification-service
public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByClerkIdOrderByCreatedAtDesc(String clerkId);
    long countByClerkIdAndReadFalse(String clerkId);
}
```

**Benefits:**
- Spring Data auto-generates implementations from method names
- No boilerplate CRUD code
- Consistent data access across all services
- Easy to add custom queries

---

### 10. DTO (Data Transfer Object) Pattern

**Purpose:** Decouple internal domain models from external API contracts; prevent over-exposure.

#### DTOs Across Services
```
user-service/src/main/java/filesharex/user/dto/
├── ProfileDTO.java
└── UserCreditsDTO.java

file-service/src/main/java/filesharex/file/dto/
└── FileMetadataDTO.java

payment-service/src/main/java/filesharex/payment/dto/
├── PaymentDTO.java
└── PaymentVerificationDTO.java

notification-service/src/main/java/filesharex/notification/model/
├── NotificationDTO.java
└── CreateNotificationRequest.java
```

**Implementation:**
```java
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileMetadataDTO {
    private String id;
    private String name;
    private String type;
    private Long size;
    private String clerkId;
    private Boolean isPublic;
    private String fileLocation;
    private LocalDateTime uploadedAt;
    private String downloadUrl;  // computed, not in entity
}

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileDTO {
    private String id;
    private String clerkId;
    private String email;
    private String firstName;
    private String lastName;
    private String photoUrl;
    private Integer credits;
    private Instant createdAt;
}
```

**Benefits:**
- Internal entities not exposed to clients
- Computed fields (e.g., `downloadUrl`) added to DTOs
- API contract independent of database schema
- Different DTOs for different operations (request vs response)

---

### 11. Mapper Pattern (Document-to-DTO)

**Purpose:** Convert between domain entities and DTOs; enrich data.

#### Mapper Methods in Services
```
file-service/src/main/java/.../service/FileMetadataService.java  → mapToDTO()
user-service/src/main/java/.../service/ProfileService.java       → mapToDTO()
notification-service/src/main/java/.../service/NotificationService.java → mapToDTO()
```

**Implementation:**
```java
// FileMetadataService (file-service)
private FileMetadataDTO mapToDTO(FileMetadataDocument doc) {
    return FileMetadataDTO.builder()
        .id(doc.getId())
        .fileLocation(doc.getFileLocation())
        .name(doc.getName())
        .size(doc.getSize())
        .type(doc.getType())
        .clerkId(doc.getClerkId())
        .isPublic(doc.getIsPublic())
        .uploadedAt(doc.getUploadedAt())
        .downloadUrl(storageFacade.getDownloadUrl(doc.getFileLocation()))  // enriched
        .build();
}

// ProfileService (user-service)
private ProfileDTO mapToDTO(ProfileDocument profile) {
    return ProfileDTO.builder()
        .id(profile.getId())
        .clerkId(profile.getClerkId())
        .email(profile.getEmail())
        .firstName(profile.getFirstName())
        .lastName(profile.getLastName())
        .photoUrl(profile.getPhotoUrl())
        .credits(profile.getCredits())
        .createdAt(profile.getCreatedAt())
        .build();
}

// NotificationService (notification-service)
private NotificationDTO mapToDTO(Notification notification) {
    return NotificationDTO.builder()
        .id(notification.getId())
        .type(notification.getType())
        .title(notification.getTitle())
        .message(notification.getMessage())
        .read(notification.isRead())
        .referenceId(notification.getReferenceId())
        .referenceType(notification.getReferenceType())
        .createdAt(notification.getCreatedAt() != null
            ? notification.getCreatedAt().format(FORMATTER) : null)
        .build();
}
```

**Benefits:**
- Enrichment logic (e.g., download URLs) centralized
- Database schema changes don't break API contracts
- Each service controls its own DTO mapping

---

### 12. Global Exception Handling Pattern (Cross-Cutting Concern)

**Purpose:** Centralized error handling; consistent error responses across all endpoints.

#### GlobalExceptionHandler (All Business Services)
```
payment-service/src/.../exception/GlobalExceptionHandler.java
file-service/src/.../exception/GlobalExceptionHandler.java
user-service/src/.../exception/GlobalExceptionHandler.java
notification-service/src/.../exception/GlobalExceptionHandler.java
```

**Implementation:**
```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        log.error("RuntimeException occurred", ex);
        Map<String, Object> data = new HashMap<>();
        data.put("status", HttpStatus.BAD_REQUEST);
        data.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(data);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex) {
        log.error("Exception occurred", ex);
        Map<String, Object> data = new HashMap<>();
        data.put("status", HttpStatus.INTERNAL_SERVER_ERROR);
        data.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(data);
    }
}

// user-service additionally handles:
@ExceptionHandler(DuplicateKeyException.class)
public ResponseEntity<?> handleDuplicateKeyException(DuplicateKeyException ex) {
    Map<String, Object> data = new HashMap<>();
    data.put("status", HttpStatus.CONFLICT);
    data.put("message", "Resource already exists");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(data);
}
```

**Benefits:**
- Consistent error response format across all services
- No try-catch in controllers
- Logging centralized
- HTTP status codes standardized

---

### 13. Circuit Breaker Pattern (Resilience4j)

**Purpose:** Prevent cascade failures; graceful degradation when dependencies are unavailable.

#### UserClient (File Service → User Service)
```
file-service/src/main/java/filesharex/file/service/UserClient.java
payment-service/src/main/java/filesharex/payment/service/PaymentService.java
```

**Implementation:**
```java
@Component
@Slf4j
public class UserClient {
    private final RestClient restClient;
    @Value("${services.user.base-url}")
    private String userBaseUrl;

    public UserClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "creditsFallback")
    public int getCredits(String clerkId) {
        Integer credits = restClient.get()
            .uri(userBaseUrl + "/internal/users/{clerkId}/credits", clerkId)
            .retrieve()
            .body(Integer.class);
        return credits != null ? credits : 0;
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "consumeFallback")
    public void consumeCredit(String clerkId) {
        restClient.post()
            .uri(userBaseUrl + "/internal/users/{clerkId}/credits/consume", clerkId)
            .retrieve()
            .toBodilessEntity();
    }

    public int creditsFallback(String clerkId, Exception e) {
        log.warn("User Service unavailable for credit check: {}", e.getMessage());
        throw new RuntimeException("User Service is currently unavailable. Please try again later.");
    }

    public void consumeFallback(String clerkId, Exception e) {
        log.warn("User Service unavailable for credit consumption: {}", e.getMessage());
        throw new RuntimeException("User Service is currently unavailable. Credit not consumed.");
    }
}
```

**Configuration:**
```yaml
resilience4j.circuitbreaker.instances.userService:
  failure-rate-threshold: 50
  wait-duration-in-open-state: 30s
  sliding-window-size: 10
  permitted-number-of-calls-in-half-open-state: 3
```

**Circuit States:**
```
CLOSED (normal) → failure rate > 50% → OPEN (30s) → HALF-OPEN (3 calls) → CLOSED/OPEN
```

**Benefits:**
- Prevents cascade failures between services
- Graceful degradation with fallback methods
- Configurable thresholds per service pair
- Monitoring via Resilience4j metrics

---

### 14. Filter / Intercepting Filter Pattern

**Purpose:** Process requests before/after they reach handlers; cross-cutting concerns.

#### Gateway JWT Authentication Filter
```
gateway/src/main/java/filesharex/gateway/security/
├── ClerkJwtAuthFilter.java (GlobalFilter)
└── ../config/SecurityConfig.java (SecurityWebFilterChain)
```

**Implementation:**
```java
@Component
@RequiredArgsConstructor
public class ClerkJwtAuthFilter implements GlobalFilter, Ordered {

    private final ClerkJwksProvider jwksProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Skip auth for public endpoints
        if (path.contains("/webhooks") || path.contains("/public") ||
            path.contains("/download") || path.contains("/health")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // Validate JWT, extract clerkId, inject as header
        String token = authHeader.substring(7);
        // ... JWT parsing and validation ...
        String clerkId = claims.getSubject();

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .header("X-Clerk-Id", clerkId)
            .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() { return -1; }  // runs early
}
```

**Security Config:**
```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/webhooks/**", "/files/public/**",
                    "/files/download/**", "/health", "/actuator/**").permitAll()
                .anyExchange().authenticated())
            .build();
    }
}
```

**Filter Chain Flow:**
```
Request → SecurityConfig (path matching) → ClerkJwtAuthFilter (JWT validation)
        → Route filter (path matching) → StripPrefix filter → Downstream service
```

**Benefits:**
- Authentication logic centralized in gateway
- Downstream services don't handle JWT validation
- Public endpoints bypassed efficiently
- Request mutation (X-Clerk-Id header) transparent to clients

---

### 15. Client Pattern (Service-to-Service Communication)

**Purpose:** Encapsulate inter-service calls; provide clean API for service communication.

#### UserClient (File Service → User Service)
```
file-service/src/main/java/filesharex/file/service/UserClient.java
```

**Implementation:**
```java
@Component
public class UserClient {
    private final RestClient restClient;
    @Value("${services.user.base-url}")
    private String userBaseUrl;

    public UserClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "creditsFallback")
    public int getCredits(String clerkId) {
        return restClient.get()
            .uri(userBaseUrl + "/internal/users/{clerkId}/credits", clerkId)
            .retrieve()
            .body(Integer.class);
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "consumeFallback")
    public void consumeCredit(String clerkId) {
        restClient.post()
            .uri(userBaseUrl + "/internal/users/{clerkId}/credits/consume", clerkId)
            .retrieve()
            .toBodilessEntity();
    }
}
```

#### PaymentService → User Service (Inline RestClient)
```java
@Service
public class PaymentService {
    private final RestClient.Builder restClientBuilder;
    @Value("${services.user.base-url}")
    private String userBaseUrl;

    private void addCreditsToUser(String clerkId, int amount, String plan) {
        restClientBuilder.build().post()
            .uri(userBaseUrl + "/internal/users/{clerkId}/credits/add", clerkId)
            .body(Map.of("amount", amount, "plan", plan))
            .retrieve()
            .toBodilessEntity();
    }

    private Map<String, Object> fetchProfile(String clerkId) {
        return restClientBuilder.build().get()
            .uri(userBaseUrl + "/internal/users/{clerkId}", clerkId)
            .retrieve()
            .body(Map.class);
    }
}
```

**Benefits:**
- Clean API for inter-service calls
- Circuit breaker protection on client
- Base URL externalized via config
- Spring RestClient handles HTTP details

---

### 16. Proxy Pattern (Gateway Reverse Proxy)

**Purpose:** Hide downstream services; single entry point for clients.

#### Spring Cloud Gateway Routes
```
gateway/src/main/resources/application.yml
```

**Configuration:**
```yaml
spring.cloud.gateway.routes[0].id: user-service
spring.cloud.gateway.routes[0].uri: lb://user-service
spring.cloud.gateway.routes[0].predicates[0]: Path=/api/v1.0/users/**,/api/v1.0/webhooks/**
spring.cloud.gateway.routes[0].filters[0]: StripPrefix=2

spring.cloud.gateway.routes[1].id: file-service
spring.cloud.gateway.routes[1].uri: lb://file-service
spring.cloud.gateway.routes[1].predicates[0]: Path=/api/v1.0/files/**
spring.cloud.gateway.routes[1].filters[0]: StripPrefix=2

spring.cloud.gateway.routes[2].id: payment-service
spring.cloud.gateway.routes[2].uri: lb://payment-service
spring.cloud.gateway.routes[2].predicates[0]: Path=/api/v1.0/payments/**,/api/v1.0/transactions/**
spring.cloud.gateway.routes[2].filters[0]: StripPrefix=2

spring.cloud.gateway.routes[3].id: notification-service
spring.cloud.gateway.routes[3].uri: lb://notification-service
spring.cloud.gateway.routes[3].predicates[0]: Path=/api/v1.0/notifications/**
spring.cloud.gateway.routes[3].filters[0]: StripPrefix=2
```

**Proxy Flow:**
```
Client → Gateway (8080)
        → Route matching (Path predicate)
        → Service discovery (lb:// via Eureka)
        → StripPrefix (remove /api/v1.0)
        → Forward to downstream service
        ← Response returned to client
```

**Benefits:**
- Clients only know gateway URL
- Downstream services hidden from external
- Load balancing via Eureka
- Authentication centralized at proxy

---

### 17. Externalized Configuration Pattern

**Purpose:** Centralize configuration; environment-specific values without code changes.

#### Config Server + Spring Config Import
```
config-repo/
├── application.yml          (shared config)
├── gateway.yml
├── user-service.yml
├── file-service.yml
├── payment-service.yml
└── notification-service.yml
```

**Config Server:**
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

**Service Config Import:**
```yaml
# All business services
spring:
  config:
    import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:9999}
```

**@Value Injection:**
```java
// Used in every service
@Value("${file.storage.type:local}") private String storageType;
@Value("${payment.provider:razorpay}") private String paymentProvider;
@Value("${services.user.base-url}") private String userBaseUrl;
@Value("${razorpay.key.id}") private String keyId;
@Value("${r2.endpoint}") private String endpoint;
```

**Config Hierarchy:**
```
Config Server (config-repo/) → Local application.yml → Environment variables
```

**Benefits:**
- Single source of truth for all services
- Environment-specific config without redeployment
- Local defaults for development
- Secrets externalized via environment variables

---

### 18. Singleton Pattern (Spring IoC Container)

**Purpose:** Ensure single instance per bean; shared state across requests.

#### Spring-Managed Singleton Beans
All `@Component`, `@Service`, `@RestController`, `@Configuration` beans are singletons by default.

**Key Singletons:**
| Bean | Service | Purpose |
|------|---------|---------|
| `PaymentGatewayFactory` | payment | Strategy selection |
| `FileStorageFacade` | file | Storage abstraction |
| `FileValidationChain` | file | Validation pipeline |
| `ClerkJwtAuthFilter` | gateway | JWT authentication |
| `UserCreditsService` | user | Credit management |
| `ProfileService` | user | Profile management |
| `FileMetadataService` | file | File metadata |
| `PaymentService` | payment | Payment processing |
| `NotificationService` | notification | Email notifications |

**Benefits:**
- Shared state across requests (connection pools, caches)
- Memory efficient
- Thread-safe (Spring manages lifecycle)
- Consistent behavior across requests

---

## Pattern Summary Matrix

| # | Pattern | Category | Location | Problem Solved |
|---|---------|----------|----------|----------------|
| 1 | **Factory Method** | Creational | `FileStorageFactory`, `PaymentGatewayFactory` | Decouple creation from usage |
| 2 | **Builder** | Creational | `PaymentResult`, DTOs | Complex object construction |
| 3 | **Singleton** | Creational | All Spring beans | Single instance per bean |
| 4 | **Facade** | Structural | `FileStorageFacade` | Simplify subsystem access |
| 5 | **Adapter** | Structural | `RazorpayGateway` | Integrate third-party SDK |
| 6 | **Proxy** | Structural | Spring Cloud Gateway | Hide downstream services |
| 7 | **Strategy** | Behavioural | `FileStorageStrategy`, `PaymentGateway` | Runtime algorithm selection |
| 8 | **Chain of Responsibility** | Behavioural | `FileValidationRule` | Composable validation |
| 9 | **Template Method** | Behavioural | `AbstractFileProcessingPipeline` | Algorithm skeleton with hooks |
| 10 | **Observer** | Behavioural | Spring `ApplicationEvent` | Decouple event producers/consumers |
| 11 | **Filter** | Behavioural | `ClerkJwtAuthFilter` | Request preprocessing |
| 12 | **Repository** | Data Access | `MongoRepository` interfaces | Abstract persistence |
| 13 | **DTO** | Data Transfer | All DTO classes | Decouple entities from API |
| 14 | **Mapper** | Data Transfer | `mapToDTO()` methods | Entity-to-DTO conversion |
| 15 | **Circuit Breaker** | Resilience | Resilience4j `@CircuitBreaker` | Prevent cascade failures |
| 16 | **Client** | Integration | `UserClient`, `RestClient` | Service-to-service calls |
| 17 | **Configuration** | Infrastructure | Config Server + `@Value` | Externalized config |
| 18 | **Exception Handler** | Cross-Cutting | `@RestControllerAdvice` | Centralized error handling |

---

## SOLID Principles Applied

| Principle | Implementation |
|-----------|---------------|
| **S** - Single Responsibility | Each service handles one domain; each validation rule does one check |
| **O** - Open/Closed | Strategy/Factory: new backends without modifying existing code |
| **L** - Liskov Substitution | All `FileStorageStrategy` implementations are interchangeable |
| **I** - Interface Segregation | Small focused interfaces (`FileStorageStrategy`, `PaymentGateway`) |
| **D** - Dependency Inversion | Services depend on interfaces, not concrete implementations |

---

## Pattern Interaction Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         REQUEST FLOW                                    │
│                                                                         │
│  Client                                                                 │
│    │                                                                    │
│    ▼                                                                    │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ PROXY PATTERN (Gateway)                                          │   │
│  │   └─ FILTER PATTERN (ClerkJwtAuthFilter)                        │   │
│  │       └─ SINGLETON PATTERN (Spring IoC)                          │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│    │                                                                    │
│    ▼                                                                    │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ TEMPLATE METHOD PATTERN (StandardUploadPipeline)                 │   │
│  │   └─ CHAIN OF RESPONSIBILITY (ValidationChain)                   │   │
│  │       └─ CLIENT PATTERN (UserClient)                             │   │
│  │           └─ CIRCUIT BREAKER (Resilience4j)                      │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│    │                                                                    │
│    ▼                                                                    │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ FACTORY PATTERN (FileStorageFactory)                             │   │
│  │   └─ STRATEGY PATTERN (R2FileStorageStrategy)                    │   │
│  │       └─ FACADE PATTERN (FileStorageFacade)                      │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│    │                                                                    │
│    ▼                                                                    │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ REPOSITORY PATTERN (FileMetadataRepository)                      │   │
│  │   └─ MAPPER PATTERN (mapToDTO)                                   │   │
│  │       └─ DTO PATTERN (FileMetadataDTO)                           │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│    │                                                                    │
│    ▼                                                                    │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ OBSERVER PATTERN (CreditConsumedEvent)                           │   │
│  │   └─ EVENT LISTENER (UserEventListener)                          │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│    │                                                                    │
│    ▼                                                                    │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ EXCEPTION HANDLER PATTERN (GlobalExceptionHandler)               │   │
│  │   └─ CONFIGURATION PATTERN (Externalized Config)                 │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

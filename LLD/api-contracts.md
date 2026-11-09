# API Contracts - Microservices Architecture

## Conventions

- **Base Path:** `/api/v1.0` (via Gateway)
- **Auth Header:** `Authorization: Bearer <clerk-jwt-token>`
- **Content Types:**
  - `application/json` for most endpoints
  - `multipart/form-data` for file uploads
- **Internal APIs:** Used for service-to-service communication (no auth required)

---

## Gateway Routes

| Route | Target Service | Path |
|-------|---------------|------|
| user-service | `lb://user-service` | `/api/v1.0/users/**`, `/api/v1.0/webhooks/**` |
| file-service | `lb://file-service` | `/api/v1.0/files/**` |
| payment-service | `lb://payment-service` | `/api/v1.0/payments/**`, `/api/v1.0/transactions/**` |

---

## User Service (Port 8081)

### Health
- `GET /health`
- **Response:** `200 OK`

### Clerk Webhooks
- `POST /webhooks/clerk`
- **Headers:** `svix-id`, `svix-timestamp`, `svix-signature`
- **Body:** Clerk event payload
- **Response:** `200 OK`

### User Credits (Authenticated)
- `GET /users/credits`
- **Response:**
```json
{
  "credits": 42,
  "plan": "PREMIUM"
}
```

### Internal APIs (Service-to-Service)

#### Get Profile
- `GET /internal/users/{clerkId}`
- **Response:**
```json
{
  "id": "string",
  "clerkId": "string",
  "email": "string",
  "firstName": "string",
  "lastName": "string",
  "photoUrl": "string",
  "credits": 42,
  "createdAt": "2024-01-01T00:00:00Z"
}
```

#### Get Credits
- `GET /internal/users/{clerkId}/credits`
- **Response:** `42` (integer)

#### Consume Credit
- `POST /internal/users/{clerkId}/credits/consume`
- **Response:**
```json
{
  "credits": 41,
  "plan": "PREMIUM"
}
```
- **Error Response:**
```json
{
  "error": "Not enough credits",
  "credits": 0
}
```

#### Add Credits
- `POST /internal/users/{clerkId}/credits/add`
- **Request:**
```json
{
  "amount": 500,
  "plan": "PREMIUM"
}
```
- **Response:**
```json
{
  "credits": 542,
  "plan": "PREMIUM"
}
```

---

## File Service (Port 8082)

### Upload Files (Authenticated)
- `POST /files/upload`
- **Request:** `multipart/form-data`
  - `files`: File(s) to upload
- **Response:**
```json
{
  "files": [
    {
      "id": "string",
      "name": "document.pdf",
      "type": "application/pdf",
      "size": 1024,
      "clerkId": "string",
      "isPublic": false,
      "fileLocation": "uploads/clerk-123/uuid.pdf",
      "uploadedAt": "2024-01-01T00:00:00",
      "downloadUrl": "https://pub-xxx.r2.dev/uploads/clerk-123/uuid.pdf"
    }
  ]
}
```

### Get My Files (Authenticated)
- `GET /files/my`
- **Response:** Array of file metadata objects

### Get Public File
- `GET /files/public/{id}`
- **Response:** File metadata object

### Download File
- `GET /files/download/{id}`
- **Response:** Binary file stream

### Toggle Public (Authenticated)
- `PATCH /files/{id}/toggle-public`
- **Response:** Updated file metadata

### Delete File (Authenticated)
- `DELETE /files/{id}`
- **Response:** `204 No Content`

---

## Payment Service (Port 8083)

### Create Order (Authenticated)
- `POST /payments/create-order`
- **Request:**
```json
{
  "planId": "premium",
  "amount": 49900,
  "currency": "INR"
}
```
- **Response:**
```json
{
  "orderId": "order_abc123",
  "success": true,
  "message": "Order created successfully",
  "isMock": false
}
```

### Verify Payment (Authenticated)
- `POST /payments/verify-payment`
- **Request:**
```json
{
  "razorpay_order_id": "order_abc123",
  "razorpay_payment_id": "pay_xyz789",
  "razorpay_signature": "signature",
  "planId": "premium"
}
```
- **Response:**
```json
{
  "success": true,
  "message": "Payment verified and credits added successfully",
  "credits": 500
}
```

### Get Transactions (Authenticated)
- `GET /transactions`
- **Response:** Array of transaction objects
```json
[
  {
    "id": "string",
    "orderId": "order_abc123",
    "paymentId": "pay_xyz789",
    "planId": "premium",
    "amount": 49900,
    "currency": "INR",
    "creditsAdded": 500,
    "status": "SUCCESS",
    "transactionDate": "2024-01-01T00:00:00",
    "userEmail": "user@example.com",
    "userName": "John Doe"
  }
]
```

---

## Error Responses

### Standard Error Format
```json
{
  "status": "BAD_REQUEST",
  "message": "Error description"
}
```

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 204 | No Content |
| 400 | Bad Request / Validation Error |
| 401 | Unauthorized (missing/invalid token) |
| 403 | Forbidden |
| 404 | Resource Not Found |
| 409 | Conflict (duplicate key) |
| 500 | Internal Server Error |
| 503 | Service Unavailable (circuit breaker open) |

---

## Circuit Breaker Behavior

When User Service is unavailable:
- File Service returns `503 Service Unavailable`
- Payment Service returns `503 Service Unavailable`
- Retry after 30 seconds (configurable)

---

## Monitoring Endpoints

### All Services
- `GET /actuator/health` — Health status
- `GET /actuator/prometheus` — Metrics for Prometheus
- `GET /actuator/metrics` — JVM, HTTP metrics

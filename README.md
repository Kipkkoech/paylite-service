PayLite - Minimal Payments Microservice

A Dockerized, minimal payments microservice that handles payment intent creation, status queries, and PSP webhook callbacks with full idempotency and security.

🚀 Quick Start

Prerequisites

•	Docker & Docker Compose

•	Java 17 (for local development)

# Running the Application
# 1.	Clone and start the stack:

bash

      git clone https://github.com/Kipkkoech/paylite-service.git

      cd paylite-service

      mvn clean install

      cp .env.test .env

      docker-compose up -d

2.	Verify services are running:

    bash
    docker-compose ps
# Should show: paylite-mysql and paylite-app running

    curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}
# 3.	Import Postman Collection

Import PaymentLite.postman_collection.json with the following structure:

o	Payments

    	Create Payment (Dynamic UUID)

    	Create Payment (Static UUID → 409 Conflict)

    	Create Payment (Invalid API KEY → 401 Unauthorized)

    	Get Payment by ID (Valid)

    	Get Payment by ID (Invalid → 404)

o	Webhooks

    	PSP Webhook (Succeeded)
    	PSP Webhook (Failed)
    	PSP Webhook (Duplicate → No-op)
    	PSP Webhook (Invalid secret → 401)
o	Health Check

	Ping API

📋 API Endpoints
1. Create Payment Intent

POST /api/v1/payments

Headers:

•	X-API-Key: <api-key> (required)

•	Idempotency-Key: <uuid> (required)

•	Content-Type: application/json

Body:

    json

    {
      "amount": 1999,
      "currency": "KES",
      "customerEmail": "user@example.com",
      "reference": "INV-2025-0001"
    }

Response:

json

    {
      "paymentId": "pl_a1b2c3d4",
      "status": "PENDING"
    }

2. Get Payment Status

GET /api/v1/payments/{paymentId}

Headers:

    •	X-API-Key: <api-key> (required)

Response:

json

    {

      "paymentId": "pl_a1b2c3d4",
      "status": "PENDING",
      "amount": 1999,
      "currency": "KES",
      "reference": "INV-2025-0001",
      "customerEmail": "user@example.com"
     }

3. PSP Webhook

POST /api/v1/webhooks/psp

Headers:

      •	X-PSP-Signature: <hmac-sha256-signature> (required)

     •	Content-Type: application/json

Body:

json

      {
        "paymentId": "pl_a1b2c3d4",
        "event": "payment.succeeded"
       }
🧪 Testing Endpoints

Payments Endpoints

✅ Create Payment (Dynamic UUID)

       •	Auto-generates Idempotency-Key
       •	First run → 200 Created
       •	paymentId is saved for later requests
✅ Create Payment (Static Idempotency-Key)

      •	First run → 201 Created
      •	Second run → original response (no duplicates)

⚠️ Create Payment (Same key - Diff Payload)

      •	First run → 200 Created
      •	Second run with changed payload (e.g., currency) → 409 Conflict
❌ Create Payment (Invalid API KEY)

    •	Returns 401 Unauthorized

🔍 Get Payment by ID (Valid)

    • 	Uses {{paymentId}} from creation request (run Create Payment first)
    •	Returns current payment status
❌ Get Payment by ID (Invalid → 404)

    •	Tries a non-existent payment ID

Webhook Endpoints

✅ PSP Webhook – Succeeded-200

    •	Uses {{paymentId}} from creation request (run Create Payment first)
    •	First run → 200
⚠️ PSP Webhook – Conflict-409

    •	Uses {{paymentId}} from creation request (run Create Payment first)
    •	First run → 200
    •	Second run with changed event type → 409 Conflict
❌ PSP Webhook – Invalid secret-401

    •	Invalid signature returns 401 Unauthorized
🏗️ Architecture & Design

Technology Stack

    •	Java 17 with Spring Boot 3.2
    •	MySQL 8.0 with Liquibase migrations
    •	Docker & Docker Compose for containerization
    •	Spring Data JPA for data access
    •	Spring Security for API authentication
Database Schema

sql

-- Payments table

     payments (id, payment_id, amount, currency, reference,customer_email, status, created_at, updated_at)

-- Idempotency keys table

      idempotency_keys (id, key, request_hash, response_body, payment_id, created_at)

-- Webhook events table

      webhook_events (id, event_id, payment_id, event_type, raw_payload, processed_at)
🛡️ Security Implementation

API Key Authentication

     •	Validates X-API-Key header against configured keys
     •	Keys externalized to environment variables
     •	Simple array-based validation for "Lite" scope
HMAC Signature Verification

    •	Validates X-PSP-Signature using shared secret
    •	SHA-256 HMAC of raw request body
    •	Prevents unauthorized webhook processing
🔄 Idempotency Design

Client → Service Idempotency

    •	Stores Idempotency-Key with request hash and response
    •	Same key + same payload = return cached response
    •	Same key + different payload = 409 Conflict
    •	Prevents duplicate payment creation
Webhook Idempotency

•	Dual deduplication strategy:

    o	Event ID-based deduplication
o	Business key-based: (paymentId, event) combination

    •	Prevents duplicate status transitions
    •	Handles PSP retries with different event IDs
📊 Transaction Safety

    •	@Transactional on service methods for atomic operations
    •	Payment creation and idempotency key storage in same transaction
    •	Webhook processing with payment status update in single transaction
    •	Pessimistic locking for webhook race condition prevention
🧪 Testing

Unit Tests

bash

     mvn test
Test Coverage

    •	Unit tests: Services, utilities, idempotency logic
    •	Integration tests: Full payment flow with H2 database
    •	Security tests: HMAC verification and API key validation
🐳 Docker Setup

Development

bash

      docker-compose up -d          # Start MySQL + App
      docker-compose logs -f paylite # View logs
      docker-compose down           # Stop services
📈 Example Flow

Acceptance Test Sequence

1.	Setup environment:

bash

    cd paylite-service
    cp .env.test .env  # copy environment variables
2.	Start stack:

bash

    docker-compose up -d
3.	Create payment: POST /api/v1/payments → Returns PENDING

4.	Idempotency test: Same request → Same paymentId

5.	Send webhook: POST /webhooks/psp → Changes status to SUCCEEDED

6.	Duplicate webhook: Same webhook → No-op, status remains SUCCEEDED

7.	Conflicting webhook: Duplicate with different payload → 409 Conflict

8.	Get payment: GET /payments/{id} → Shows final status

🎯 Design Decisions & Trade-offs

Schema Choices

•	No Foreign Keys: Better performance, handles race conditions, supports microservice evolution

•	Strategic Indexes: Optimized for payment lookups, status queries, and deduplication

•	Separate Audit Tables: Maintain history for idempotency and webhook deduplication

Performance Considerations

•	Dual Deduplication: Event ID + business key for comprehensive duplicate prevention

•	Pessimistic Locking: Prevents race conditions in webhook processing

•	Minimal Overhead: Simple validation logic for "Lite" scope
Security Implementation

•	HMAC with Raw Body: Preserves exact bytes for signature verification

•	Environment Variables: Externalized secrets configuration
•	API Key Simplicity: Array-based validation appropriate for microservice scale

🔍 Observability

Logging

    •	Structured logs with correlation IDs
    •	Key events: payment created, webhook processed, idempotency hits
    •	Security events: invalid API keys, signature failures
Health Checks

    •	Spring Boot Actuator: /actuator/health
    •	Database connectivity monitoring
    •	Readiness/Liveness endpoints
✅ Implementation Status

Fully Implemented Requirements

    •	REST endpoints match specification (create, get, webhook)
    •	Idempotency on create and webhook processing
    •	API key authentication and HMAC validation
    •	Dockerized setup with MySQL
    •	Database migrations with Liquibase
    •	Transaction safety with @Transactional
    •	Structured logging and health checks
    •	Unit and integration tests
Architecture & Code Quality

    •	Clear layering: Controller → Service → Repository
    •	Single responsibility: Separate services for payments, idempotency, webhooks, security
    •	Proper transaction boundaries: No "half-writes"
    •	Clean error handling: Meaningful HTTP responses
    •	Configuration management: Externalized secrets
Data & Persistence

    •	Schema management: Liquibase with proper constraints and indexes
    •	Efficient queries: Strategic indexes for all query patterns
    •	Transaction safety: Atomic updates for create and webhook flows
🆘 Troubleshooting

Common Issues

Application won't start:

    •	Check MySQL is running: docker-compose ps
    •	Verify environment variables are set
    •	Check logs: docker-compose logs paylite
Webhook signature failures:

    • 	Verify APP_WEBHOOK_SECRET matches between app and test client
    •	Ensure raw body is used for signature calculation
    •	Check for trailing spaces in headers
Database connection issues:

    •	Wait for MySQL to be fully ready (healthcheck)
    •	Verify credentials in .env file
    •	Check network connectivity between containers
🎯 Stretch Goals (Optional)

•	Basic rate limiting on create endpoint

•	Basic OpenAPI documentation

•	Retry policy for transient database errors

for looging, add logging with correlation using MDC


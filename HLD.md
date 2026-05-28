# SwiftPay - High-Level Design (HLD)

## 1. System Overview

SwiftPay is a **distributed, event-driven fintech platform** for real-time P2P (peer-to-peer) money transfers. The system is designed with microservices principles, emphasizing **loose coupling**, **eventual consistency**, and **horizontal scalability**.

### Vision
Build a resilient, scalable payment platform that processes high-volume transactions while maintaining strong data consistency, audit compliance, and idempotency guarantees.

---

## 2. System Architecture

### 2.1 High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Client Layer                                  │
│  (Web/Mobile Apps, Third-party Integrations, Admin Dashboards)      │
└────────────────────────────────────────────────────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
        ┌───────────▼────────────┐  ┌───────▼──────────────┐
        │  Load Balancer/API GW  │  │  Service Registry    │
        │   (NGINX/ALB)          │  │  (Consul/Eureka)     │
        └───────────┬────────────┘  └──────────────────────┘
                    │
        ┌───────────▼────────────────────────────────┐
        │   Service A: Transaction Gateway API        │
        │  (REST, Idempotency, Balance Validation)   │
        └───────────┬────────────────────────────────┘
                    │
        ┌───────────▼────────────────────────────────┐
        │  Kafka Message Broker (Event Bus)           │
        │  Topics: payment-*, ledger-*               │
        └───────────┬────────────────────────────────┘
                    │
        ┌───────────▼──────────────────────────────────┐
        │ Service B: Ledger Service (Consumer)         │
        │ (Balance Updates, Ledger Recording)          │
        └───────────┬──────────────────────────────────┘
                    │
    ┌───────────────┼───────────────┬──────────────────┐
    │               │               │                  │
┌───▼──────┐   ┌────▼──────┐  ┌───▼───────┐  ┌──────▼────────┐
│PostgreSQL│   │   Redis   │  │Analytics  │  │ Audit Log DB  │
│ Ledger   │   │  Cache    │  │ (Future)  │  │  (Immutable)  │
│Transact. │   │Idempotency│  │ClickHouse│  │   (Append)    │
└──────────┘   └───────────┘  └───────────┘  └───────────────┘
```

### 2.2 Component Layers

```
┌─────────────────────────────────────────────────────┐
│              Presentation Layer                      │
│  Controllers (Spring MVC) → REST Endpoints         │
│  Swagger UI, API Documentation                     │
└─────────────────────────────────────────────────────┘
                        │
┌─────────────────────────────────────────────────────┐
│              Business Logic Layer                    │
│  Services (PaymentService, LedgerService, etc.)    │
│  Domain Objects, Business Rules                     │
└─────────────────────────────────────────────────────┘
                        │
┌─────────────────────────────────────────────────────┐
│           Data Access & Persistence Layer            │
│  Repositories (JPA), ORM (Hibernate)               │
│  Transaction Management, Query Optimization         │
└─────────────────────────────────────────────────────┘
                        │
┌─────────────────────────────────────────────────────┐
│         Infrastructure & External Services           │
│  Kafka (Messaging), Redis (Caching)                │
│  PostgreSQL (RDBMS), Kubernetes (Orchestration)    │
└─────────────────────────────────────────────────────┘
```

---

## 3. Core System Components

### 3.1 Service A: Transaction Gateway

**Purpose**: Single entry point for payment requests; handles validation, idempotency, and event emission.

**Responsibilities**:
- Accept payment requests from clients
- Validate request parameters (amounts, currency, user IDs)
- Check idempotency via Redis (prevent duplicate processing)
- Retrieve sender's balance from cache or database
- Validate sender has sufficient balance
- Persist payment record with PENDING status
- Emit PaymentInitiated event to Kafka
- Return immediate response to client

**Key Features**:
- Stateless design for horizontal scaling
- Synchronous response (fast feedback)
- Asynchronous event emission
- Cache-backed balance validation

**Endpoints**:
```
POST   /v1/payments                      - Initiate payment
GET    /v1/payments/{transactionId}/status - Check status
GET    /v1/payments/user/{userId}       - Transaction history
```

### 3.2 Service B: Ledger Service

**Purpose**: Consumes payment events and performs atomic balance transfers; maintains audit trail.

**Responsibilities**:
- Consume PaymentInitiated events from Kafka
- Validate sender's balance (final check)
- Debit sender's account
- Credit receiver's account
- Record ledger entries (2 per transaction: debit + credit)
- Update payment status (COMPLETED or FAILED)
- Emit PaymentCompleted or PaymentFailed events
- Handle retries and error scenarios

**Key Features**:
- Event-driven architecture
- Atomic transactions (ACID)
- Idempotent processing
- Automatic retry with exponential backoff
- Ledger immutability (append-only)

**Event Processing**:
- Consumes: `payment-initiated`
- Produces: `payment-completed`, `payment-failed`

### 3.3 Supporting Services

**IdempotencyService**:
- Manages Redis-based duplicate detection
- 24-hour TTL for transaction IDs
- Prevents double-processing of payments

**BalanceService**:
- Manages user account balances
- Redis caching (5-minute TTL)
- Validates sufficient funds
- Atomic balance updates

**KafkaProducerService**:
- Emits events to Kafka topics
- Idempotent producer configuration
- Error handling and retry logic

---

## 4. Data Model

### 4.1 Entity Relationships

```
┌──────────────────────────────────────────┐
│            user_balance                   │
├──────────────────────────────────────────┤
│ id (PK)                                   │
│ user_id (UK) ◄──────────┐                │
│ balance                  │                │
│ currency                 │                │
│ version (OPT_LOCK)       │                │
│ created_at               │                │
│ updated_at               │                │
└──────────────────────────────────────────┘
                           │
        ┌──────────────────┴───────────────┐
        │                                  │
┌───────▼─────────────────────────┐  ┌────▼─────────────────────┐
│        payments                 │  │      ledger               │
├─────────────────────────────────┤  ├──────────────────────────┤
│ id (PK)                         │  │ id (PK)                  │
│ transaction_id (UK)◄──────┐     │  │ user_id (FK)◄─┐          │
│ sender_id                 │     │  │ payment_id (FK)──┐       │
│ receiver_id               │     │  │ transaction_id│   │       │
│ amount                    │     │  │ type (DEBIT/  │   │       │
│ currency                  │     │  │   CREDIT)     │   │       │
│ status (PENDING/          │     │  │ amount        │   │       │
│   COMPLETED/FAILED)       │     │  │ currency      │   │       │
│ failure_reason            │     │  │ balance_after │   │       │
│ created_at                │     │  │ description   │   │       │
│ updated_at                │     │  │ created_at    │   │       │
└─────────────────────────────────┘  └──────────────────────────┘
              │                              │
              └──────────────────────────────┘
             (One-to-Two for ledger entries)
```

### 4.2 Key Design Decisions

**Immutable Ledger**: 
- Ledger entries are INSERT-only (no updates/deletes)
- Ensures compliance and audit trail integrity

**Payment Status Machine**:
```
PENDING ──(success)──> COMPLETED
   │
   └────(failure)───> FAILED
```

**Balance Versioning**:
- Optimistic locking with `@Version` field
- Prevents race conditions in concurrent updates

---

## 5. Data Flow Architecture

### 5.1 Payment Processing Flow

```
Client Request (1)
    ↓
API Gateway / Load Balancer
    ↓
Transaction Gateway Service (2)
    ├─ Validate Input
    ├─ Check Idempotency (Redis)
    ├─ Validate Balance (Redis/DB)
    ├─ Save Payment (PENDING)
    ├─ Emit Event (Kafka)
    └─ Return 201 Created (3)
            ↓ (Async)
Kafka Topic: payment-initiated
    ↓
Ledger Service Consumer (4)
    ├─ Receive Event
    ├─ Validate Balance (Final)
    ├─ Debit Sender (TX)
    ├─ Credit Receiver (TX)
    ├─ Record Ledger (2 entries)
    ├─ Update Payment Status
    ├─ Emit PaymentCompleted
    └─ Invalidate Cache (5)
            ↓
Kafka Topic: payment-completed
    ↓
Analytics Service (Optional)
    └─ Record Metrics
```

**Synchronous Part** (1-3): User gets immediate feedback
**Asynchronous Part** (4-5): Actual debit/credit happens

---

## 6. Infrastructure Architecture

### 6.1 Deployment Options

**Local Development** (Docker Compose):
```
┌──────────────────────────────────────┐
│      Docker Compose Network           │
├──────────────────────────────────────┤
│ ┌─────────┐ ┌────────┐ ┌──────────┐ │
│ │PostgreSQL│ │ Kafka  │ │  Redis   │ │
│ └─────────┘ └────────┘ └──────────┘ │
│ ┌────────────────────────────────┐  │
│ │   SwiftPay Application         │  │
│ └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

**Production** (Kubernetes):
```
┌────────────────────────────────────────────┐
│         Kubernetes Cluster                  │
├────────────────────────────────────────────┤
│  ┌────────────────────────────────────┐   │
│  │  SwiftPay Deployment (HPA)         │   │
│  │  ├─ Pod 1 / Pod 2 / Pod 3         │   │
│  │  └─ Auto-scales (2-10 replicas)   │   │
│  └────────────────────────────────────┘   │
│  ┌────────────────────────────────────┐   │
│  │  StatefulSets (Kafka, PostgreSQL)  │   │
│  └────────────────────────────────────┘   │
│  ┌────────────────────────────────────┐   │
│  │  Services (LoadBalancer, ClusterIP)│  │
│  └────────────────────────────────────┘   │
│  ┌────────────────────────────────────┐   │
│  │  ConfigMaps & Secrets              │   │
│  └────────────────────────────────────┘   │
└────────────────────────────────────────────┘
```

### 6.2 Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Language** | Java 25 | Type-safe, enterprise-ready |
| **Framework** | Spring Boot 4.0 | Rapid development, microservices |
| **API** | Spring MVC + REST | RESTful web services |
| **Database** | PostgreSQL 15 | ACID transactions, reliability |
| **ORM** | JPA + Hibernate | Object-relational mapping |
| **Messaging** | Apache Kafka 7.5 | Event streaming, high throughput |
| **Cache** | Redis 7 | In-memory data store |
| **Container** | Docker | Containerization |
| **Orchestration** | Kubernetes 1.24+ | Container orchestration |
| **Documentation** | OpenAPI 3.0 | API specification |
| **Testing** | JUnit 5, Testcontainers | Testing framework |
| **Load Testing** | K6 | Performance testing |

---

## 7. Non-Functional Requirements

### 7.1 Performance

| Metric | Target | Strategy |
|--------|--------|----------|
| **Response Time (P95)** | < 300ms | Caching, DB indexing |
| **Throughput** | 250 TPS sustained | Partitioning, load balancing |
| **Latency (P99)** | < 800ms | Async processing |

### 7.2 Reliability

| Aspect | Method |
|--------|--------|
| **Fault Tolerance** | Kafka retry, circuit breaker |
| **High Availability** | Kubernetes replicas |
| **Data Consistency** | ACID transactions |

### 7.3 Scalability

| Dimension | Approach |
|-----------|----------|
| **Horizontal** | Stateless services, K8s HPA |
| **Vertical** | JVM tuning, connection pooling |
| **Data** | Database partitioning (future) |

### 7.4 Security

| Aspect | Implementation |
|--------|----------------|
| **Input Validation** | Bean Validation |
| **SQL Injection** | JPA parameterized queries |
| **Secrets** | Kubernetes secrets |
| **Audit** | Immutable ledger |

---

## 8. Key Design Principles

### 8.1 CAP Theorem Application

SwiftPay prioritizes **Consistency (C)** and **Availability (A)** over Partition Tolerance:

- **Consistency**: ACID transactions for balance transfers
- **Availability**: Async Kafka processing for non-blocking responses
- **Partition Tolerance**: Retry mechanism with exponential backoff

### 8.2 Resilience Patterns

**Circuit Breaker**:
- Not implemented yet, but recommended for external service calls
- Prevents cascading failures

**Bulkhead Pattern**:
- Kafka consumer threads isolated from request threads
- Prevents resource exhaustion

**Retry with Exponential Backoff**:
- Kafka consumer: 3 attempts with 1s, 2s delays
- Graceful degradation on persistent failures

### 8.3 Scalability Patterns

**Stateless Services**:
- No in-memory state beyond request scope
- Enables horizontal scaling

**Event-Driven**:
- Decouples request processing from business logic
- Async processing via Kafka

**Caching Strategy**:
- Redis for hot data (balances, idempotency)
- TTL-based expiration for freshness

---

## 9. Integration Points

### 9.1 External System Integration

```
┌─────────────────────────────────┐
│   External Payment Gateways     │
│   (Stripe, PayPal, etc.)        │
└──────────────┬──────────────────┘
               │
        ┌──────▼──────────┐
        │  SwiftPay API   │
        │  (Webhook)      │
        └─────────────────┘
```

### 9.2 Monitoring & Observability

**Logging**:
- Structured JSON logs
- Correlation IDs for tracing
- Log aggregation (ELK stack recommended)

**Metrics**:
- Request latency (histogram)
- Error rates (counter)
- Kafka consumer lag (gauge)

**Tracing**:
- Distributed tracing with Jaeger (future)
- Trace request across services

---

## 10. Deployment & Operations

### 10.1 Deployment Pipeline

```
Code Commit
    ↓
GitHub Actions Trigger
    ├─ Compile
    ├─ Test
    ├─ Build Docker Image
    └─ Push to Registry
            ↓
Kubernetes Deployment
    ├─ Pull Image
    ├─ Create Pods
    ├─ Health Checks
    └─ Traffic Routing
```

### 10.2 Operational Dashboards

- **Kubernetes Dashboard**: Pod metrics, resource usage
- **Prometheus/Grafana**: Application metrics
- **Log Aggregation**: Centralized logging
- **Distributed Tracing**: Request flow visualization

---

## 11. System Boundaries & Assumptions

### 11.1 Assumptions

1. All users pre-exist in the system
2. Single currency (USD) for MVP
3. Payment amounts are validated client-side and server-side
4. Kafka is always available (retry handles temporary downtime)
5. PostgreSQL provides ACID guarantees

### 11.2 Boundaries

- **Out of Scope**: User authentication, authorization, fraud detection
- **Future**: Multi-currency, scheduled transactions, transfer history exports

---

## 12. Risk Analysis

| Risk | Impact | Mitigation |
|------|--------|-----------|
| **Kafka broker failure** | High | Replication, cluster mode |
| **Database lock contention** | Medium | Partitioning, read replicas |
| **Cache miss cascade** | Medium | Circuit breaker, fallback |
| **Duplicate transactions** | Critical | Idempotency cache, deduplication |

---

## 13. Future Enhancements

- [ ] Multi-currency support
- [ ] Real-time analytics with ClickHouse
- [ ] Machine learning for fraud detection
- [ ] Advanced user authentication (OAuth2)
- [ ] Payment scheduling
- [ ] Webhook notifications
- [ ] Rate limiting & quotas
- [ ] GraphQL API

---

## 14. Success Metrics

| Metric | Target |
|--------|--------|
| **API Response Time (P99)** | < 1000ms |
| **Transaction Success Rate** | > 99.5% |
| **System Availability** | > 99.9% |
| **Kafka Consumer Lag** | < 5 seconds |
| **Cache Hit Ratio** | > 80% |

---

## Summary

SwiftPay is a **modern, distributed fintech platform** built on:

✅ **Microservices**: Loosely coupled services  
✅ **Event-Driven**: Asynchronous processing  
✅ **Cloud-Native**: Kubernetes-ready  
✅ **Scalable**: Horizontal scaling  
✅ **Reliable**: ACID transactions + retries  
✅ **Observable**: Logging, metrics, tracing  

The architecture prioritizes **consistency** and **availability** while maintaining **performance** through caching, indexing, and async processing.



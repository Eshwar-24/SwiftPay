# SwiftPay: Real-Time Payment Ledger Platform

A resilient, scalable fintech platform for peer-to-peer (P2P) money transfers built with Java 25, Spring Boot, PostgreSQL, Apache Kafka, and Redis.

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Technical Stack](#technical-stack)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [Database Schema](#database-schema)
- [Deployment](#deployment)
- [Load Testing](#load-testing)
- [Monitoring & Health Checks](#monitoring--health-checks)
- [Error Handling](#error-handling)
- [Performance Considerations](#performance-considerations)

## 🏗️ Architecture

SwiftPay is built using a microservices-inspired event-driven architecture:

```
┌─────────────────────────────────────────────────────────┐
│                   Client Applications                    │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        │                         │
    ┌───▼────────────┐   ┌───────▼──────────┐
    │  Payment API   │   │  Ledger API      │
    │  (Service A)   │   │  (Service B)     │
    └───────┬────────┘   └────────┬─────────┘
            │                     │
            └──────────┬──────────┘
                       │
                 ┌─────▼──────┐
                 │   Kafka    │  (Event Bus)
                 │  Events    │
                 └─────┬──────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
    ┌───▼────┐  ┌──────▼──┐  ┌──────▼──────┐
    │PostgreSQL│  │ Redis   │  │ Analytics   │
    │(Ledger)   │  │(Caching)│  │(Service C)  │
    └──────────┘  └─────────┘  └─────────────┘
```

### Services

1. **Service A: Transaction Gateway (REST API)**
   - Accepts payment requests via `/v1/payments` endpoint
   - Validates idempotency using Redis (24-hour window)
   - Checks sender's balance from cache or database
   - Stores initial transaction as PENDING in PostgreSQL
   - Emits `PaymentInitiated` event to Kafka

2. **Service B: Ledger Service (Event Processor)**
   - Consumes `PaymentInitiated` events from Kafka
   - Performs atomic debit/credit operations via database transactions
   - Records transaction history in ledger table
   - Emits `PaymentCompleted` or `PaymentFailed` events
   - Includes automatic retry mechanism for kafka consumer failures
   - Exposes `/v1/ledger/user/{userId}` endpoint for transaction history

3. **Service C: Analytics Worker (Bonus)**
   - Consumes `PaymentCompleted` events
   - Aggregates transaction data in real-time
   - Tracks transaction volumes and patterns
   - Sends metrics to monitoring systems

## 🛠️ Technical Stack

- **Language**: Java 25
- **Framework**: Spring Boot 4.0.7
- **Database**: PostgreSQL 15
- **Message Queue**: Apache Kafka 7.5.0
- **Cache**: Redis 7
- **Container Orchestration**: Kubernetes / Minikube
- **CI/CD**: GitHub Actions
- **API Documentation**: OpenAPI 3.0 / Swagger UI
- **Testing**: JUnit 5, Testcontainers
- **Load Testing**: K6

## ✨ Features

### Core Features
- ✅ Idempotent payment processing (prevents duplicate transactions)
- ✅ Real-time transaction status tracking
- ✅ Balance validation and atomic transfers
- ✅ Comprehensive audit logging
- ✅ Error handling with proper HTTP status codes
- ✅ Transaction history with ledger entries

### Operational Features
- ✅ Health check endpoint (`/health`)
- ✅ API documentation via Swagger UI
- ✅ Structured logging with SLF4J
- ✅ Database connection pooling
- ✅ Kafka retry mechanism with exponential backoff
- ✅ Redis caching with TTL

### Infrastructure Features
- ✅ Docker containerization
- ✅ Docker Compose for local development
- ✅ Kubernetes manifests for production
- ✅ GitHub Actions CI/CD pipeline
- ✅ Horizontal Pod Autoscaling (HPA)
- ✅ Health checks and probes

## 📦 Prerequisites

### Local Development
- Java 25 JDK
- Maven 3.8+
- Docker & Docker Compose
- Git

### For Kubernetes Deployment
- Kubernetes 1.24+ or Minikube
- kubectl CLI
- Container registry access

## 🚀 Getting Started

### 1. Clone the Repository

```bash
cd java-Hackathon/SwiftPay
```

### 2. Build the Application

```bash
./mvnw clean package
```

### 3. Run with Docker Compose

```bash
docker-compose up -d
```

This will start:
- PostgreSQL (port 5432)
- Apache Kafka (port 9092)
- Redis (port 6379)
- SwiftPay Application (port 8080)

### 4. Verify Setup

```bash
# Check health
curl -X GET http://localhost:8080/health

# View Swagger UI
open http://localhost:8080/swagger-ui.html
```

### 5. Initialize Database (if needed)

The database is automatically initialized via the `init-db.sql` script. This creates:
- `payments` table (for transaction records)
- `ledger` table (for audit trail)
- `user_balance` table (for account balances)
- Sample users with initial balances

## 📚 API Documentation

### Base URL
```
http://localhost:8080
```

### Endpoints

#### 1. Initiate Payment
```
POST /v1/payments
Content-Type: application/json

{
  "transactionId": "TXN-001",
  "senderId": "user1",
  "receiverId": "user2",
  "amount": 100.00,
  "currency": "USD"
}

Response: 201 Created
{
  "success": true,
  "message": "Payment initiated successfully",
  "data": {
    "id": 1,
    "transactionId": "TXN-001",
    "senderId": "user1",
    "receiverId": "user2",
    "amount": 100.00,
    "currency": "USD",
    "status": "PENDING",
    "createdAt": "2026-05-27T10:00:00",
    "updatedAt": "2026-05-27T10:00:00"
  }
}
```

**Status Codes:**
- `201 Created`: Payment initiated successfully
- `400 Bad Request`: Validation error (insufficient balance, invalid input)
- `409 Conflict`: Duplicate transaction (idempotency violation)
- `500 Internal Server Error`: Server error

#### 2. Get Payment Status
```
GET /v1/payments/{transactionId}/status

Response: 200 OK
{
  "success": true,
  "message": "Payment status retrieved successfully",
  "data": {
    "status": "COMPLETED" | "PENDING" | "FAILED"
  }
}
```

#### 3. Get Transaction History
```
GET /v1/payments/user/{userId}

Response: 200 OK
{
  "success": true,
  "message": "Transaction history retrieved successfully",
  "data": [
    {
      "transactionId": "TXN-001",
      "senderId": "user1",
      "receiverId": "user2",
      "amount": 100.00,
      "status": "COMPLETED",
      "createdAt": "2026-05-27T10:00:00"
    }
  ]
}
```

#### 4. Get Ledger History (with balances)
```
GET /v1/ledger/user/{userId}

Response: 200 OK
{
  "success": true,
  "message": "Transaction history retrieved successfully",
  "data": {
    "userId": "user1",
    "currentBalance": 9900.00,
    "currency": "USD",
    "transactions": [
      {
        "transactionId": "TXN-001",
        "counterpartyId": "user2",
        "type": "SENT",
        "amount": 100.00,
        "status": "COMPLETED",
        "createdAt": "2026-05-27T10:00:00"
      }
    ]
  }
}
```

#### 5. Health Check
```
GET /health

Response: 200 OK
{
  "status": "UP",
  "service": "SwiftPay",
  "timestamp": 1685164800000
}
```

### Swagger UI
Access the interactive API documentation at:
```
http://localhost:8080/swagger-ui.html
```

## 🗄️ Database Schema

### payments Table
```sql
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(255) UNIQUE NOT NULL,
    sender_id VARCHAR(255) NOT NULL,
    receiver_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_transaction_id ON payments(transaction_id);
CREATE INDEX idx_sender_id ON payments(sender_id);
CREATE INDEX idx_receiver_id ON payments(receiver_id);
CREATE INDEX idx_status ON payments(status);
```

### ledger Table
```sql
CREATE TABLE ledger (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    payment_id BIGINT NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    type VARCHAR(10) NOT NULL, -- 'DEBIT' or 'CREDIT'
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance_after NUMERIC(19,2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_id ON ledger(payment_id);
CREATE INDEX idx_user_id ON ledger(user_id);
```

### user_balance Table
```sql
CREATE TABLE user_balance (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) UNIQUE NOT NULL,
    balance NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    version BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_balance ON user_balance(user_id);
```

## 🐳 Docker Deployment

### Docker Compose
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f swiftpay

# Stop services
docker-compose down

# Cleanup volumes
docker-compose down -v
```

### Building Docker Image
```bash
docker build -t swiftpay:latest .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/swiftpay \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e SPRING_REDIS_HOST=redis \
  swiftpay:latest
```

## ☸️ Kubernetes Deployment

### Prerequisites
- Kubernetes cluster running (or Minikube)
- kubectl configured

### Deploy to Kubernetes
```bash
# Create namespace and deploy all resources
kubectl apply -f k8s-deployment.yaml

# Verify deployment
kubectl get pods -n swiftpay
kubectl get svc -n swiftpay

# Check logs
kubectl logs -f deployment/swiftpay -n swiftpay

# Port forward to access locally
kubectl port-forward svc/swiftpay-service 8080:80 -n swiftpay

# Access application
curl http://localhost:8080/health
```

### Scaling
```bash
# Manual scaling
kubectl scale deployment swiftpay --replicas=5 -n swiftpay

# HPA (Horizontal Pod Autoscaler) is automatically configured
# It scales based on CPU (70%) and Memory (80%) utilization
```

### Cleanup
```bash
kubectl delete namespace swiftpay
```

## 📊 Load Testing

### Using K6

#### Installation
```bash
# Install K6
brew install k6  # macOS
choco install k6  # Windows
```

#### Run Load Test
```bash
# Light load test
k6 run load-test.js --vus 10 --duration 1m

# 250 TPS load test (30 minutes)
k6 run load-test.js

# Generate results in JSON
k6 run load-test.js -o json=results.json
```

#### Load Test Scenarios
The included `load-test.js` performs:
1. **Ramp-up**: 0 → 250 TPS over 2 minutes
2. **Sustained**: 250 TPS for 30 minutes (1M transactions total)
3. **Ramp-down**: 250 → 0 TPS over 2 minutes

#### Expected Metrics
- **95th Percentile Response Time**: < 500ms
- **99th Percentile Response Time**: < 1000ms
- **Error Rate**: < 1%
- **Total Transactions**: ~1 million
- **Average TPS**: 250

### Generating PCAP Trace

To capture network traffic during load testing:

```bash
# In separate terminal, start packet capture
tcpdump -i any -w swiftpay-load-test.pcap port 8080 or port 9092 or port 5432

# Run load test
k6 run load-test.js

# Stop tcpdump (Ctrl+C)

# Analyze with Wireshark
wireshark swiftpay-load-test.pcap
```

## 🏥 Monitoring & Health Checks

### Health Check Endpoint
```bash
curl -X GET http://localhost:8080/health

Response:
{
  "status": "UP",
  "service": "SwiftPay",
  "timestamp": 1685164800000
}
```

### Logging
Logs are configured with different levels:
- `DEBUG`: Detailed information about payment processing
- `INFO`: General operational information
- `WARN`: Warning messages (idempotency violations, insufficient funds)
- `ERROR`: Error conditions

View logs:
```bash
docker-compose logs -f swiftpay
# or
kubectl logs -f deployment/swiftpay -n swiftpay
```

### Database Health
```bash
# Check PostgreSQL connection
psql -h localhost -U postgres -d swiftpay -c "SELECT 1"

# Monitor active connections
psql -h localhost -U postgres -d swiftpay -c "SELECT datname, count(*) FROM pg_stat_activity GROUP BY datname"
```

### Kafka Health
```bash
# Check broker status
docker-compose exec kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092

# List topics
docker-compose exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Redis Health
```bash
# Check Redis connection
redis-cli -h localhost ping

# Monitor commands
redis-cli -h localhost monitor
```

## ⚠️ Error Handling

### Error Response Format
```json
{
  "success": false,
  "message": "User-friendly error message",
  "error": {
    "code": "ERROR_CODE",
    "message": "Detailed error message",
    "details": "Additional context if available"
  }
}
```

### Error Codes and HTTP Status Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| IDEMPOTENCY_VIOLATION | 409 Conflict | Duplicate transaction within 24 hours |
| INSUFFICIENT_FUNDS | 402 Payment Required | Sender has insufficient balance |
| USER_NOT_FOUND | 404 Not Found | User account does not exist |
| VALIDATION_ERROR | 400 Bad Request | Invalid input parameters |
| INTERNAL_ERROR | 500 Internal Server Error | Unexpected server error |

### Kafka Consumer Retry Strategy
- **Initial Delay**: 1 second
- **Max Attempts**: 3
- **Backoff Multiplier**: 2.0 (exponential)
- **Max Delay**: Configurable

```
Retry Timeline:
Attempt 1: Immediate
Attempt 2: After 1 second
Attempt 3: After 2 seconds
```

### Database Transaction Handling
- Uses Spring's `@Transactional` annotation for ACID compliance
- Automatic rollback on exceptions
- Optimistic locking via version field for concurrent updates

## 📈 Performance Considerations

### Optimization Strategies

1. **Caching** (Redis)
   - User balances cached for 5 minutes
   - Idempotency keys cached for 24 hours
   - Automatic cache invalidation on updates

2. **Database**
   - Connection pooling (HikariCP)
   - Batch processing (batch_size=20)
   - Strategic indexing on frequently queried columns
   - Partitioning strategy for large tables

3. **Kafka**
   - Partitioning for parallel processing
   - Consumer group for load distribution
   - Manual commit for reliability
   - Idempotent producer configuration

4. **API**
   - Async processing where possible
   - Response pagination for large datasets
   - HTTP compression enabled

### Bottleneck Identification (from load test)

Common bottlenecks in payment systems:
1. **Database Write Lock**: Mitigated with partitioning
2. **Network Latency**: Mitigated with caching
3. **Message Queue Lag**: Mitigated with consumer scaling
4. **Connection Pool Exhaustion**: Mitigated with proper pooling

## 🧪 Testing

### Run Unit Tests
```bash
./mvnw test
```

### Run Integration Tests
```bash
./mvnw verify
```

### Test Coverage
Tests cover:
- Payment initiation and idempotency
- Balance validation and transfer
- Error handling and exception cases
- Kafka event production and consumption
- Redis cache operations

## 📖 CI/CD Pipeline

### GitHub Actions Workflow
The `.github/workflows/build-test-deploy.yml` file defines:

1. **Build Stage**
   - Compile Java source code
   - Resolve dependencies

2. **Test Stage**
   - Run unit tests
   - Run integration tests with Testcontainers
   - Services: PostgreSQL, Kafka, Redis (Docker containers)

3. **Quality Stage**
   - Code quality analysis
   - Checkstyle violations check

4. **Security Stage**
   - Dependency vulnerability scanning
   - OWASP dependency check

5. **Artifact Stage**
   - Build Docker image
   - Generate build logs

### Triggering Pipeline
- Push to `main` or `develop` branches
- Pull requests

## 🔐 Security Considerations

- Input validation on all API endpoints
- SQL injection prevention via JPA
- Idempotent API design
- Transactional integrity
- Audit logging of all transactions
- Rate limiting recommendations (not yet implemented, can be added)

## 📝 Deployment Checklist

- [ ] Database migrations executed
- [ ] Kafka topics created
- [ ] Redis cache initialized
- [ ] Health checks passing
- [ ] Load test executed
- [ ] PCAP trace captured
- [ ] Documentation updated
- [ ] Security scan passed
- [ ] Performance baselines met

## 🆘 Troubleshooting

### Application won't start
```bash
# Check logs
docker-compose logs swiftpay

# Verify dependencies are running
docker-compose ps

# Check port availability
lsof -i :8080
```

### Payment transactions stuck in PENDING
```bash
# Check Kafka consumer lag
docker-compose exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group swiftpay-group \
  --describe

# Manually check Kafka topics
docker-compose exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Database connection issues
```bash
# Test connection
psql -h localhost -U postgres -d swiftpay

# Check connection pool
docker-compose exec postgres psql -U postgres -c "SELECT count(*) FROM pg_stat_activity"
```

## 📞 Support

For issues, create a GitHub issue with:
- Error logs
- Steps to reproduce
- Environment details

## 📄 License

This project is submitted as part of the SwiftPay Hackathon Challenge.

## 👥 Contributors

- SwiftPay Development Team
- Built during Hackathon Challenge (May 2026)

---

**Happy Payment Processing! 🚀**


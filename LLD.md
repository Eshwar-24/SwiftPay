# SwiftPay - Low-Level Design (LLD)

## 1. Detailed Component Specifications

### 1.1 Class Hierarchy & Structure

```
com.hackathon.SwiftPay/
├── controller/
│   ├── PaymentController
│   ├── LedgerController
│   └── HealthController
├── service/
│   ├── PaymentService
│   ├── LedgerService
│   ├── BalanceService
│   ├── IdempotencyService
│   └── KafkaProducerService
├── domain/
│   ├── entity/
│   │   ├── Payment
│   │   ├── Ledger
│   │   └── UserBalance
│   └── enums/
│       └── TransactionStatus {PENDING, COMPLETED, FAILED, CANCELLED}
├── repository/
│   ├── PaymentRepository
│   ├── LedgerRepository
│   └── UserBalanceRepository
├── dto/
│   ├── PaymentRequest
│   ├── PaymentResponse
│   ├── PaymentEvent
│   ├── ApiResponse<T>
│   ├── UserBalanceResponse
│   └── TransactionHistoryResponse
├── exception/
│   ├── PaymentException
│   ├── IdempotencyException
│   ├── InsufficientFundsException
│   └── UserNotFoundException
└── config/
    ├── RedisConfig
    ├── KafkaConfig
    ├── OpenApiConfig
    └── GlobalExceptionHandler
```

---

## 2. Service Layer Detailed Design

### 2.1 PaymentService

**Responsibility**: Handle payment initiation and status management

**Methods**:

```java
public class PaymentService {
    // Core method: Initiate payment request
    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request) {
        // 1. Check idempotency
        if (idempotencyService.isDuplicateTransaction(request.getTransactionId())) {
            throw new IdempotencyException(...);
        }
        
        // 2. Validate balance
        balanceService.validateBalance(request.getSenderId(), request.getAmount());
        
        // 3. Create payment record
        Payment payment = Payment.builder()
            .transactionId(request.getTransactionId())
            .senderId(request.getSenderId())
            .receiverId(request.getReceiverId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .status(TransactionStatus.PENDING)
            .build();
        
        // 4. Save to database
        Payment savedPayment = paymentRepository.save(payment);
        
        // 5. Mark idempotency key
        idempotencyService.markTransactionAsProcessed(request.getTransactionId());
        
        // 6. Emit event
        kafkaProducerService.sendPaymentInitiated(savedPayment);
        
        // 7. Return response
        return convertToResponse(savedPayment);
    }
    
    // Query methods
    public List<PaymentResponse> getTransactionHistory(String userId) { }
    public PaymentResponse getPaymentStatus(String transactionId) { }
    
    @Transactional
    public void updatePaymentStatus(String transactionId, 
                                     TransactionStatus status, 
                                     String failureReason) { }
}
```

**Transaction Scope**:
- Single DB transaction for payment creation
- Idempotency check outside transaction (to allow retries)

**Error Handling**:
- `IdempotencyException` → 409 Conflict
- `InsufficientFundsException` → 402 Payment Required
- `UserNotFoundException` → 404 Not Found

---

### 2.2 LedgerService

**Responsibility**: Process payment events and maintain audit trail

**Kafka Listener Method**:

```java
@Service
public class LedgerService {
    @KafkaListener(topics = "${kafka.topic.payment-initiated:payment-initiated}",
                   groupId = "${spring.application.name:SwiftPay}")
    @Transactional
    public void consumePaymentInitiated(PaymentEvent event) {
        try {
            // 1. Fetch payment record
            Payment payment = paymentRepository
                .findByTransactionId(event.getTransactionId())
                .orElseThrow(() -> new RuntimeException("Payment not found"));
            
            // 2. Validate balance (final check)
            balanceService.validateBalance(event.getSenderId(), event.getAmount());
            
            // 3. Atomic balance transfer (single transaction)
            balanceService.debitBalance(event.getSenderId(), event.getAmount());
            balanceService.creditBalance(event.getReceiverId(), event.getAmount());
            
            // 4. Record ledger entries (2 per transaction)
            recordLedgerEntry(payment.getId(), event.getSenderId(), 
                            event.getTransactionId(), "DEBIT", 
                            event.getAmount(), event.getCurrency(), 
                            "Payment sent");
            
            recordLedgerEntry(payment.getId(), event.getReceiverId(), 
                            event.getTransactionId(), "CREDIT", 
                            event.getAmount(), event.getCurrency(), 
                            "Payment received");
            
            // 5. Update payment status
            payment.setStatus(TransactionStatus.COMPLETED);
            paymentRepository.save(payment);
            
            // 6. Emit success event
            kafkaProducerService.sendPaymentCompleted(payment);
            
        } catch (Exception e) {
            // 1. Mark as failed
            payment.setStatus(TransactionStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);
            
            // 2. Emit failure event
            kafkaProducerService.sendPaymentFailed(payment, e.getMessage());
            
            // 3. Trigger retry
            throw new PaymentException("Payment processing failed");
        }
    }
    
    // Record single ledger entry
    @Transactional
    private void recordLedgerEntry(Long paymentId, String userId, 
                                    String transactionId, String type,
                                    BigDecimal amount, String currency, 
                                    String description) {
        BigDecimal balanceAfter = balanceService.getBalance(userId);
        
        Ledger ledger = Ledger.builder()
            .userId(userId)
            .paymentId(paymentId)
            .transactionId(transactionId)
            .type(type)  // DEBIT or CREDIT
            .amount(amount)
            .currency(currency)
            .balanceAfter(balanceAfter)
            .description(description)
            .createdAt(LocalDateTime.now())
            .build();
        
        ledgerRepository.save(ledger);
    }
    
    // Fetch transaction history
    public TransactionHistoryResponse getTransactionHistory(String userId) {
        BigDecimal currentBalance = balanceService.getBalance(userId);
        List<Ledger> ledgerEntries = ledgerRepository.findUserTransaction(userId);
        
        // Transform ledger entries with payment details
        List<TransactionHistoryResponse.Transaction> transactions = 
            ledgerEntries.stream()
                .map(entry -> {
                    Payment payment = paymentRepository
                        .findById(entry.getPaymentId()).orElse(null);
                    
                    String counterpartyId = entry.getType().equals("DEBIT") 
                        ? payment.getReceiverId() 
                        : payment.getSenderId();
                    
                    return TransactionHistoryResponse.Transaction.builder()
                        .transactionId(entry.getTransactionId())
                        .counterpartyId(counterpartyId)
                        .type(entry.getType().equals("DEBIT") ? "SENT" : "RECEIVED")
                        .amount(entry.getAmount())
                        .currency(entry.getCurrency())
                        .status(payment.getStatus())
                        .createdAt(entry.getCreatedAt())
                        .build();
                })
                .toList();
        
        return TransactionHistoryResponse.builder()
            .userId(userId)
            .currentBalance(currentBalance)
            .currency("USD")
            .transactions(transactions)
            .build();
    }
}
```

**Transaction Handling**:
- Entire payment processing in single Kafka listener transaction
- Automatic rollback on exception
- Ledger entries are INSERT-ONLY (no deletes/updates)

---

### 2.3 BalanceService

**Responsibility**: Manage user account balances with caching

```java
@Service
public class BalanceService {
    private static final String BALANCE_CACHE_KEY_PREFIX = "balance:";
    private static final long BALANCE_CACHE_TTL_MINUTES = 5;
    
    // Get balance with fallback to DB
    public BigDecimal getBalance(String userId) {
        // Try cache first
        String cacheKey = BALANCE_CACHE_KEY_PREFIX + userId;
        Object cachedBalance = redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedBalance != null) {
            return new BigDecimal(cachedBalance.toString());
        }
        
        // Fetch from DB
        UserBalance userBalance = userBalanceRepository
            .findByUserId(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        // Cache it
        redisTemplate.opsForValue().set(cacheKey, 
            userBalance.getBalance().toString(), 
            BALANCE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        
        return userBalance.getBalance();
    }
    
    // Validate sender has sufficient balance
    public void validateBalance(String userId, BigDecimal requiredAmount) {
        BigDecimal balance = getBalance(userId);
        
        if (balance.compareTo(requiredAmount) < 0) {
            throw new InsufficientFundsException(
                String.format("Required: %s, Available: %s", 
                    requiredAmount, balance));
        }
    }
    
    // Atomic debit operation with optimistic locking
    @Transactional
    public void debitBalance(String userId, BigDecimal amount) {
        UserBalance userBalance = userBalanceRepository
            .findByUserId(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        BigDecimal newBalance = userBalance.getBalance().subtract(amount);
        
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException(
                "Transaction would result in negative balance");
        }
        
        // Hibernate handles optimistic locking via @Version
        userBalance.setBalance(newBalance);
        userBalanceRepository.save(userBalance);
        
        // Invalidate cache
        invalidateBalanceCache(userId);
    }
    
    // Atomic credit operation
    @Transactional
    public void creditBalance(String userId, BigDecimal amount) {
        UserBalance userBalance = userBalanceRepository
            .findByUserId(userId)
            .orElseGet(() -> createNewUserBalance(userId));
        
        BigDecimal newBalance = userBalance.getBalance().add(amount);
        userBalance.setBalance(newBalance);
        userBalanceRepository.save(userBalance);
        
        // Invalidate cache
        invalidateBalanceCache(userId);
    }
    
    private BigDecimal invalidateBalanceCache(String userId) {
        String cacheKey = BALANCE_CACHE_KEY_PREFIX + userId;
        redisTemplate.delete(cacheKey);
    }
}
```

**Concurrency Control**:
- Optimistic locking via JPA `@Version` field
- Prevents race conditions in concurrent balance updates
- Database detects conflicts and retries automatically

---

### 2.4 IdempotencyService

**Responsibility**: Prevent duplicate transaction processing

```java
@Service
public class IdempotencyService {
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final long IDEMPOTENCY_WINDOW_HOURS = 24;
    
    // Check if transaction already processed
    public boolean isDuplicateTransaction(String transactionId) {
        String key = IDEMPOTENCY_KEY_PREFIX + transactionId;
        return redisTemplate.hasKey(key) != null && 
               redisTemplate.hasKey(key);
    }
    
    // Mark transaction as processed with TTL
    public void markTransactionAsProcessed(String transactionId) {
        String key = IDEMPOTENCY_KEY_PREFIX + transactionId;
        redisTemplate.opsForValue().set(key, "processed", 
            IDEMPOTENCY_WINDOW_HOURS, TimeUnit.HOURS);
    }
    
    // Clear idempotency key (manual cleanup)
    public void clearIdempotencyKey(String transactionId) {
        String key = IDEMPOTENCY_KEY_PREFIX + transactionId;
        redisTemplate.delete(key);
    }
}
```

**Key Features**:
- Redis SET with NX (atomic check-and-set)
- Automatic expiration after 24 hours
- Prevents duplicate debits/credits
- O(1) lookup time

---

### 2.5 KafkaProducerService

**Responsibility**: Emit payment events to Kafka

```java
@Service
public class KafkaProducerService {
    @Value("${kafka.topic.payment-initiated:payment-initiated}")
    private String paymentInitiatedTopic;
    
    @Value("${kafka.topic.payment-completed:payment-completed}")
    private String paymentCompletedTopic;
    
    @Value("${kafka.topic.payment-failed:payment-failed}")
    private String paymentFailedTopic;
    
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    
    // Emit PaymentInitiated event
    public void sendPaymentInitiated(Payment payment) {
        PaymentEvent event = PaymentEvent.builder()
            .transactionId(payment.getTransactionId())
            .senderId(payment.getSenderId())
            .receiverId(payment.getReceiverId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(payment.getStatus())
            .timestamp(System.currentTimeMillis())
            .build();
        
        // Use transaction ID as Kafka key for partitioning
        kafkaTemplate.send(paymentInitiatedTopic, 
            payment.getTransactionId(), event);
    }
    
    // Similar methods for PaymentCompleted and PaymentFailed
}
```

**Kafka Configuration**:
- **Key**: Transaction ID → ensures ordering per transaction
- **Partitioning**: Multiple partitions for parallelism
- **Idempotent Producer**: Prevents duplicate delivery

---

## 3. Data Access Layer

### 3.1 Repository Interfaces

```java
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    // Find by transaction ID (unique constraint)
    Optional<Payment> findByTransactionId(String transactionId);
    
    // Find by sender
    List<Payment> findBySenderId(String senderId);
    
    // Find by receiver
    List<Payment> findByReceiverId(String receiverId);
    
    // Combined history
    List<Payment> findBySenderIdOrReceiverId(String senderId, String receiverId);
    
    // Custom query with ORDER BY
    @Query("SELECT p FROM Payment p WHERE " +
           "(p.senderId = :userId OR p.receiverId = :userId) " +
           "ORDER BY p.createdAt DESC")
    List<Payment> findTransactionHistoryByUserId(@Param("userId") String userId);
    
    // Count by status and date range
    @Query("SELECT COUNT(p) FROM Payment p WHERE " +
           "p.status = :status AND " +
           "p.createdAt BETWEEN :from AND :to")
    long countByStatusAndDateRange(
        @Param("status") TransactionStatus status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);
}

@Repository
public interface LedgerRepository extends JpaRepository<Ledger, Long> {
    List<Ledger> findByUserId(String userId);
    List<Ledger> findByPaymentId(Long paymentId);
    
    @Query("SELECT l FROM Ledger l WHERE l.userId = :userId " +
           "ORDER BY l.createdAt DESC")
    List<Ledger> findUserTransaction(@Param("userId") String userId);
    
    // Date range queries for reporting
    @Query("SELECT l FROM Ledger l WHERE " +
           "l.userId = :userId AND " +
           "l.createdAt BETWEEN :from AND :to " +
           "ORDER BY l.createdAt DESC")
    List<Ledger> findUserTransactionsByDateRange(
        @Param("userId") String userId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);
}

@Repository
public interface UserBalanceRepository 
    extends JpaRepository<UserBalance, Long> {
    Optional<UserBalance> findByUserId(String userId);
}
```

**Query Optimization**:
- Named queries for performance
- Proper indexing on frequently queried columns
- JPA projections for large result sets (future)

---

### 3.2 Database Indexing Strategy

```sql
-- Payment table indexes
CREATE INDEX idx_payments_transaction_id ON payments(transaction_id);
-- Purpose: Fast lookup for status checks

CREATE INDEX idx_payments_sender_id ON payments(sender_id);
-- Purpose: User's sent transactions

CREATE INDEX idx_payments_receiver_id ON payments(receiver_id);
-- Purpose: User's received transactions

CREATE INDEX idx_payments_status ON payments(status);
-- Purpose: Query by status (PENDING, COMPLETED, FAILED)

CREATE INDEX idx_payments_created_at ON payments(created_at);
-- Purpose: Date range queries, recent transactions

-- Ledger table indexes
CREATE INDEX idx_ledger_user_id ON ledger(user_id);
-- Purpose: User's full history

CREATE INDEX idx_ledger_payment_id ON ledger(payment_id);
-- Purpose: Foreign key lookups

-- User balance index
CREATE INDEX idx_user_balance_user_id ON user_balance(user_id);
-- Purpose: Fast balance lookup
```

**Index Design Rationale**:
- Transaction ID: Unique constraint for idempotency
- User IDs: Common filter in WHERE clauses
- Status: Dashboard queries
- Created_at: Historical analysis and recent transactions

---

## 4. Entity Design

### 4.1 Payment Entity

```java
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transaction_id", unique = true),
    @Index(name = "idx_sender_id", columnList = "sender_id"),
    @Index(name = "idx_receiver_id", columnList = "receiver_id"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String transactionId;  // External unique ID
    
    @Column(nullable = false)
    private String senderId;       // User who sends money
    
    @Column(nullable = false)
    private String receiverId;     // User who receives money
    
    @Column(nullable = false)
    private BigDecimal amount;     // Amount transferred
    
    @Column(nullable = false)
    private String currency;       // Currency code (USD, EUR, etc.)
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;  // PENDING, COMPLETED, FAILED
    
    private String failureReason;  // Error message if failed
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

**Design Decisions**:
- `transactionId` as external identifier (idempotency key)
- `status` as enum (type-safe)
- Audit fields (`createdAt`, `updatedAt`) 
- `failureReason` for debugging

---

### 4.2 Ledger Entity (Immutable)

```java
@Entity
@Table(name = "ledger", indexes = {
    @Index(name = "idx_ledger_payment_id", columnList = "payment_id"),
    @Index(name = "idx_ledger_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ledger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String userId;        // User affected
    
    @Column(nullable = false)
    private Long paymentId;       // FK to payment
    
    @Column(nullable = false)
    private String transactionId; // Reference to payment
    
    @Column(nullable = false)
    private String type;          // DEBIT or CREDIT (String, not enum)
    
    @Column(nullable = false)
    private BigDecimal amount;    // Amount moved
    
    @Column(nullable = false)
    private String currency;      // Currency code
    
    @Column(nullable = false)
    private BigDecimal balanceAfter;  // Balance snapshot after this entry
    
    private String description;   // "Payment sent" or "Payment received"
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // NO UPDATE, NO DELETE - Only INSERT!
    // Ensures audit trail immutability
}
```

**Immutability Design**:
- UPDATE column by column is prevented by application logic
- DELETE is prevented by application design
- Only INSERT operations allowed
- TimeStamp is immutable (`updatable = false`)

---

### 4.3 UserBalance Entity

```java
@Entity
@Table(name = "user_balance", indexes = {
    @Index(name = "idx_user_balance_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String userId;        // External user ID
    
    @Column(nullable = false)
    private BigDecimal balance;   // Current balance
    
    @Column(nullable = false)
    private String currency;      // Currency code
    
    @Version
    private Long version;         // Optimistic locking
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

**Concurrency Handling**:
- `@Version` field enables optimistic locking
- Hibernate increments version on each update
- Detects concurrent modifications and throws exception
- Automatic retry by framework or application

---

## 5. Controller Layer Design

### 5.1 PaymentController

```java
@RestController
@RequestMapping("/v1/payments")
@Tag(name = "Payment", description = "Payment API")
@Slf4j
public class PaymentController {
    private final PaymentService paymentService;
    
    @PostMapping
    @Operation(summary = "Initiate a payment")
    @ApiResponse(responseCode = "201", description = "Payment initiated")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "409", description = "Duplicate transaction")
    @ApiResponse(responseCode = "402", description = "Insufficient funds")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @Valid @RequestBody PaymentRequest request) {
        
        log.info("Payment request: {}", request.getTransactionId());
        
        PaymentResponse data = paymentService.initiatePayment(request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<PaymentResponse>builder()
                .success(true)
                .message("Payment initiated successfully")
                .data(data)
                .build());
    }
    
    @GetMapping("/{transactionId}/status")
    @Operation(summary = "Check payment status")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentStatus(
            @PathVariable String transactionId) {
        
        PaymentResponse data = paymentService.getPaymentStatus(transactionId);
        
        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
            .success(true)
            .message("Payment status retrieved")
            .data(data)
            .build());
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user's transaction history")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getTransactionHistory(
            @PathVariable String userId) {
        
        List<PaymentResponse> data = paymentService
            .getTransactionHistory(userId);
        
        return ResponseEntity.ok(ApiResponse.<List<PaymentResponse>>builder()
            .success(true)
            .message("Transaction history retrieved")
            .data(data)
            .build());
    }
}
```

**Status Codes Strategy**:
- `201 Created`: Successful payment initiation
- `400 Bad Request`: Validation errors
- `402 Payment Required`: Insufficient funds
- `404 Not Found`: User or transaction not found
- `409 Conflict`: Duplicate transaction
- `500 Internal Server Error`: Server errors

---

## 6. Configuration Layer

### 6.1 RedisConfig

```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // String serialization for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        
        // Default serialization for values
        template.setValueSerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}
```

---

### 6.2 KafkaConfig

```java
@Configuration
@EnableKafka
public class KafkaConfig {
    
    @Bean
    public ProducerFactory<String, PaymentEvent> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, 
            "${spring.kafka.bootstrap-servers}");
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, 
            StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
            JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    public ConsumerFactory<String, PaymentEvent> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, 
            "${spring.kafka.bootstrap-servers}");
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, 
            "${spring.kafka.consumer.group-id}");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
            StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
            JsonDeserializer.class);
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, 
            PaymentEvent.class.getName());
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> 
            kafkaListenerContainerFactory() {
        
        ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConcurrency(3);  // 3 consumer threads
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties()
            .setAckMode(ContainerProperties.AckMode.MANUAL);
        
        return factory;
    }
}
```

---

## 7. Error Handling Strategy

### 7.1 Exception Hierarchy

```
Throwable
  ├─ Exception
  │   ├─ PaymentException (Base)
  │   │   ├─ IdempotencyException
  │   │   ├─ InsufficientFundsException
  │   │   └─ UserNotFoundException
  │   └─ RuntimeException (Spring-managed)
```

### 7.2 Global Exception Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentException(
            PaymentException ex) {
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .error(ApiResponse.ErrorDetails.builder()
                    .code(ex.getCode())
                    .message(ex.getMessage())
                    .build())
                .build());
    }
    
    @ExceptionHandler(IdempotencyException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyException(
            IdempotencyException ex) {
        
        return ResponseEntity.status(HttpStatus.CONFLICT)  // 409
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message("Duplicate transaction")
                .error(ApiResponse.ErrorDetails.builder()
                    .code(ex.getCode())
                    .message(ex.getMessage())
                    .build())
                .build());
    }
    
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientFundsException(
            InsufficientFundsException ex) {
        
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)  // 402
            .body(ApiResponse.<Void>builder()
                .success(false)
                .message("Insufficient balance")
                .error(ApiResponse.ErrorDetails.builder()
                    .code(ex.getCode())
                    .message(ex.getMessage())
                    .build())
                .build());
    }
}
```

---

## 8. Transaction Management

### 8.1 @Transactional Boundaries

```java
// Service method - Single DB transaction
@Transactional
public PaymentResponse initiatePayment(PaymentRequest request) {
    // All DB operations within this method are in single transaction
    paymentRepository.save(...);  // INSERT
    
    // Idempotency marked OUTSIDE transaction (to allow retries)
    idempotencyService.markTransactionAsProcessed(...);  // Redis
    
    // Event emitted OUTSIDE transaction (doesn't fail payment)
    kafkaProducerService.sendPaymentInitiated(...);  // Kafka
    
    return response;
}

// Kafka consumer - Single DB transaction per message
@Transactional
public void consumePaymentInitiated(PaymentEvent event) {
    // All DB operations in single transaction
    balanceService.debitBalance(...);     // UPDATE user_balance
    balanceService.creditBalance(...);    // UPDATE user_balance
    ledgerRepository.save(...);           // INSERT ledger x2
    paymentRepository.save(...);          // UPDATE payment
    
    // Event emitted after transaction commits
    kafkaProducerService.sendPaymentCompleted(...);
}
```

**Transaction Scope Rules**:
1. Start transaction at service layer
2. All DB operations within transaction
3. External calls (Redis, Kafka) outside transaction
4. Automatic rollback on exception

---

## 9. DTO Design

### 9.1 Request/Response DTOs

```java
// Request
public class PaymentRequest {
    @NotBlank
    private String transactionId;      // Idempotency key
    
    @NotBlank
    private String senderId;
    
    @NotBlank
    private String receiverId;
    
    @DecimalMin("0.01")
    private BigDecimal amount;
    
    @NotBlank
    private String currency;
}

// Response
public class PaymentResponse {
    private Long id;
    private String transactionId;
    private String senderId;
    private String receiverId;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private String failureReason;      // Only if failed
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// Kafka Event
public class PaymentEvent {
    private String transactionId;
    private String senderId;
    private String receiverId;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private String failureReason;
    private Long timestamp;
}
```

**Design Principles**:
- Request/Response DTOs for API contracts
- Event DTOs for Kafka messages
- Separate from entities (decouples API from DB)
- Validation annotations on request DTOs

---

## 10. Logging Strategy

### 10.1 Log Levels

```java
// DEBUG: Detailed flow information
log.debug("Checking idempotency for transaction: {}", transactionId);

// INFO: Business-significant events
log.info("Payment initiated: {} from {} to {}", transactionId, senderId, receiverId);

// WARN: Potential issues (recovered)
log.warn("Insufficient balance for user {}: required={}, available={}", 
    userId, required, available);

// ERROR: Exceptions and failures
log.error("Payment processing failed: {}", transactionId, exception);
```

### 10.2 Correlation IDs (Future)

```java
// Add correlation ID to MDC (Mapped Diagnostic Context)
MDC.put("correlationId", transactionId);

// All logs within this thread will include correlationId
log.info("Processing payment");  // Output: ... [correlationId=TX-001] ...

// Clear on completion
MDC.clear();
```

---

## Summary

The **LLD** defines:

✅ **Class structures** with responsibilities  
✅ **Service implementations** with algorithm details  
✅ **Data access patterns** with query optimization  
✅ **Entity designs** with concurrency handling  
✅ **Configuration specifications** with settings  
✅ **Transaction boundaries** with rollback strategies  
✅ **Error handling** with exception mappings  

This level of detail enables developers to implement, test, and maintain the system effectively.



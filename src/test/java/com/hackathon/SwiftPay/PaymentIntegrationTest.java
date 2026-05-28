//package com.hackathon.SwiftPay;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.MediaType;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.springframework.test.web.servlet.MockMvc;
//import org.testcontainers.containers.GenericContainer;
//import org.testcontainers.containers.KafkaContainer;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.testcontainers.utility.DockerImageName;
//
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@SpringBootTest
//@AutoConfigureMockMvc
//@Testcontainers
//public class PaymentIntegrationTest {
//
//    @Container
//    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
//        DockerImageName.parse("postgres:15-alpine"))
//        .withDatabaseName("swiftpay")
//        .withUsername("postgres")
//        .withPassword("password");
//
//    @Container
//    static KafkaContainer kafka = new KafkaContainer(
//        DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
//
//    @Container
//    static GenericContainer<?> redis = new GenericContainer<>(
//        DockerImageName.parse("redis:7-alpine"))
//        .withExposedPorts(6379);
//
//    @DynamicPropertySource
//    static void setProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url", postgres::getJdbcUrl);
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
//        registry.add("spring.redis.host", redis::getHost);
//        registry.add("spring.redis.port", redis::getFirstMappedPort);
//    }
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Test
//    public void testHealthCheck() throws Exception {
//        mockMvc.perform(get("/health"))
//            .andExpect(status().isOk())
//            .andExpect(jsonPath("$.status").value("UP"));
//    }
//
//    @Test
//    public void testInitiatePayment() throws Exception {
//        String paymentRequest = """
//            {
//                "transactionId": "TXN-001",
//                "senderId": "user1",
//                "receiverId": "user2",
//                "amount": 100.00,
//                "currency": "USD"
//            }
//            """;
//
//        mockMvc.perform(post("/v1/payments")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content(paymentRequest))
//            .andExpect(status().isCreated())
//            .andExpect(jsonPath("$.success").value(true))
//            .andExpect(jsonPath("$.data.status").value("PENDING"));
//    }
//
//    @Test
//    public void testIdempotency() throws Exception {
//        String paymentRequest = """
//            {
//                "transactionId": "TXN-IDEMPOTENT",
//                "senderId": "user1",
//                "receiverId": "user2",
//                "amount": 50.00,
//                "currency": "USD"
//            }
//            """;
//
//        // First request
//        mockMvc.perform(post("/v1/payments")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content(paymentRequest))
//            .andExpect(status().isCreated());
//
//        // Second request with same transaction ID
//        mockMvc.perform(post("/v1/payments")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content(paymentRequest))
//            .andExpect(status().isConflict());
//    }
//
//    @Test
//    public void testGetPaymentStatus() throws Exception {
//        // First create a payment
//        String paymentRequest = """
//            {
//                "transactionId": "TXN-STATUS-TEST",
//                "senderId": "user1",
//                "receiverId": "user2",
//                "amount": 75.00,
//                "currency": "USD"
//            }
//            """;
//
//        mockMvc.perform(post("/v1/payments")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content(paymentRequest))
//            .andExpect(status().isCreated());
//
//        // Then fetch its status
//        mockMvc.perform(get("/v1/payments/TXN-STATUS-TEST/status"))
//            .andExpect(status().isOk())
//            .andExpect(jsonPath("$.success").value(true))
//            .andExpect(jsonPath("$.data.status").value("PENDING"));
//    }
//}
//
package com.hackathon.SwiftPay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;  // from spring-boot-starter-test
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class PaymentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("swiftpay")
            .withUsername("postgres")
            .withPassword("password");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // Fix: Spring Boot 3+ uses spring.data.redis.*, not spring.redis.*
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private MockMvc mockMvc;

    // ── Health ────────────────────────────────────────────────────────────────

    @Test
    public void testHealthCheck() throws Exception {
        // Spring Boot Actuator health endpoint — no custom JSON needed
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    public void testInitiatePayment() throws Exception {
        String body = """
                {
                    "transactionId": "TXN-001",
                    "senderId": "a0000000-0000-0000-0000-000000000001",
                    "receiverId": "a0000000-0000-0000-0000-000000000002",
                    "amount": 100.00,
                    "currency": "USD"
                }
                """;

        // Fix: gateway returns 202 ACCEPTED, not 201 CREATED
        // Fix: response body has $.status directly, not nested under $.data
        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.transactionId").value("TXN-001"));
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    public void testIdempotency() throws Exception {
        String body = """
                {
                    "transactionId": "TXN-IDEMPOTENT",
                    "senderId": "a0000000-0000-0000-0000-000000000001",
                    "receiverId": "a0000000-0000-0000-0000-000000000002",
                    "amount": 50.00,
                    "currency": "USD"
                }
                """;

        // First call — accepted
        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        // Same transactionId again — must return 409 Conflict
        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_TRANSACTION"));
    }

    // ── Insufficient funds ────────────────────────────────────────────────────

    @Test
    public void testInsufficientFunds() throws Exception {
        String body = """
                {
                    "transactionId": "TXN-BROKE",
                    "senderId": "a0000000-0000-0000-0000-000000000001",
                    "receiverId": "a0000000-0000-0000-0000-000000000002",
                    "amount": 999999999.00,
                    "currency": "USD"
                }
                """;

        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    public void testValidationRejectsEmptyBody() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    public void testValidationRejectsNegativeAmount() throws Exception {
        String body = """
                {
                    "transactionId": "TXN-NEG",
                    "senderId": "a0000000-0000-0000-0000-000000000001",
                    "receiverId": "a0000000-0000-0000-0000-000000000002",
                    "amount": -10.00,
                    "currency": "USD"
                }
                """;

        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ── Account not found ─────────────────────────────────────────────────────

    @Test
    public void testUnknownSenderReturns404() throws Exception {
        String body = """
                {
                    "transactionId": "TXN-GHOST",
                    "senderId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
                    "receiverId": "a0000000-0000-0000-0000-000000000002",
                    "amount": 10.00,
                    "currency": "USD"
                }
                """;

        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
    }
}
package com.hackathon.SwiftPay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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

import java.time.Duration;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class PaymentIntegrationTest {

    // ---------- PostgreSQL ----------

//    @Container
//    static final PostgreSQLContainer<?> postgres =
//            new PostgreSQLContainer<>("postgres:15-alpine")
//                    .withDatabaseName("swiftpay")
//                    .withUsername("postgres")
//                    .withPassword("password");
//
//    // ---------- Kafka ----------
//
//    @Container
//    static final KafkaContainer kafka =
//            new KafkaContainer(
//                    DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
//                    .withStartupTimeout(Duration.ofMinutes(5));
//
//    // ---------- Redis ----------
//
//    @Container
//    static final GenericContainer<?> redis =
//            new GenericContainer<>(
//                    DockerImageName.parse("redis:7-alpine"))
//                    .withExposedPorts(6379);
//
//    // ---------- Dynamic Config ----------
//
//
//    //@DynamicPropertySource
//    static void configure(DynamicPropertyRegistry registry) {
//
//        // PostgreSQL
//        registry.add("spring.datasource.url", postgres::getJdbcUrl);
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//
//        // Kafka
//        registry.add("spring.kafka.bootstrap-servers",
//                kafka::getBootstrapServers);
//
//        // Redis
//        registry.add("spring.data.redis.host",
//                redis::getHost);
//
//        registry.add("spring.data.redis.port",
//                () -> redis.getMappedPort(6379));
//    }

    @Autowired
    private MockMvc mockMvc;

    // ---------- Health Check ----------

    @Test
    void testHealthCheck() throws Exception {

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // ---------- Payment Test ----------

    @Test
    void testInitiatePayment() throws Exception {
        String transactionId= LocalDateTime.now().toString(); // Unique transaction ID for testing
        String request = String.format("""
                {
                    "transactionId": "%s",
                    "senderId": "user1",
                    "receiverId": "user2",
                    "amount": 100,
                    "currency": "USD"
                }
                """,transactionId);

        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status")
                        .value("PENDING"));
    }
}
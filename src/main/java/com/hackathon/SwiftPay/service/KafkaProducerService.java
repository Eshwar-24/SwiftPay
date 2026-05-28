package com.hackathon.SwiftPay.service;

import com.hackathon.SwiftPay.domain.entity.Payment;
import com.hackathon.SwiftPay.dto.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaProducerService {

    @Value("${kafka.topic.payment-initiated:payment-initiated}")
    private String paymentInitiatedTopic;

    @Value("${kafka.topic.payment-completed:payment-completed}")
    private String paymentCompletedTopic;

    @Value("${kafka.topic.payment-failed:payment-failed}")
    private String paymentFailedTopic;

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

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

        kafkaTemplate.send(paymentInitiatedTopic, payment.getTransactionId(), event);
        log.info("Sent PaymentInitiated event for transaction: {}", payment.getTransactionId());
    }

    public void sendPaymentCompleted(Payment payment) {
        PaymentEvent event = PaymentEvent.builder()
            .transactionId(payment.getTransactionId())
            .senderId(payment.getSenderId())
            .receiverId(payment.getReceiverId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(payment.getStatus())
            .timestamp(System.currentTimeMillis())
            .build();

        kafkaTemplate.send(paymentCompletedTopic, payment.getTransactionId(), event);
        log.info("Sent PaymentCompleted event for transaction: {}", payment.getTransactionId());
    }

    public void sendPaymentFailed(Payment payment, String failureReason) {
        PaymentEvent event = PaymentEvent.builder()
            .transactionId(payment.getTransactionId())
            .senderId(payment.getSenderId())
            .receiverId(payment.getReceiverId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .status(payment.getStatus())
            .failureReason(failureReason)
            .timestamp(System.currentTimeMillis())
            .build();

        kafkaTemplate.send(paymentFailedTopic, payment.getTransactionId(), event);
        log.info("Sent PaymentFailed event for transaction: {}", payment.getTransactionId());
    }
}


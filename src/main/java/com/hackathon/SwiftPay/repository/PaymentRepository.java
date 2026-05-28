package com.hackathon.SwiftPay.repository;

import com.hackathon.SwiftPay.domain.entity.Payment;
import com.hackathon.SwiftPay.domain.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findBySenderId(String senderId);

    List<Payment> findByReceiverId(String receiverId);

    List<Payment> findBySenderIdOrReceiverId(String senderId, String receiverId);

    @Query("SELECT p FROM Payment p WHERE (p.senderId = :userId OR p.receiverId = :userId) ORDER BY p.createdAt DESC")
    List<Payment> findTransactionHistoryByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status AND p.createdAt >= :from AND p.createdAt <= :to")
    long countByStatusAndDateRange(@Param("status") TransactionStatus status,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);
}


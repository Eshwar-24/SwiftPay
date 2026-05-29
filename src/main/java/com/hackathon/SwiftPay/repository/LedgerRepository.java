package com.hackathon.SwiftPay.repository;

import com.hackathon.SwiftPay.domain.entity.Ledger;
import com.hackathon.SwiftPay.dto.LedgerTransactionProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LedgerRepository extends JpaRepository<Ledger, Long> {

    List<Ledger> findByUserId(String userId);

    List<Ledger> findByPaymentId(Long paymentId);

    @Query("""
        SELECT l.transactionId AS transactionId,
               CASE WHEN l.type = 'DEBIT' THEN p.receiverId ELSE p.senderId END AS counterpartyId,
                l.type AS transactionType,
               l.amount AS amount,
               l.currency AS currency,
               p.status AS status,
               l.createdAt AS createdAt
        FROM Ledger l
        LEFT JOIN Payment p ON p.id = l.paymentId
        WHERE l.userId = :userId
        ORDER BY l.createdAt DESC
        """)
    Slice<LedgerTransactionProjection> findUserTransactionHistory(@Param("userId") String userId,
                                                                  Pageable pageable);

    @Query("SELECT l FROM Ledger l WHERE l.userId = :userId AND l.createdAt BETWEEN :from AND :to ORDER BY l.createdAt DESC")
    List<Ledger> findUserTransactionsByDateRange(@Param("userId") String userId,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);
}


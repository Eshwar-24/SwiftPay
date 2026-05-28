package com.hackathon.SwiftPay.repository;

import com.hackathon.SwiftPay.domain.entity.Ledger;
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

    @Query("SELECT l FROM Ledger l WHERE l.userId = :userId ORDER BY l.createdAt DESC")
    List<Ledger> findUserTransaction(@Param("userId") String userId);

    @Query("SELECT l FROM Ledger l WHERE l.userId = :userId AND l.createdAt BETWEEN :from AND :to ORDER BY l.createdAt DESC")
    List<Ledger> findUserTransactionsByDateRange(@Param("userId") String userId,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);
}


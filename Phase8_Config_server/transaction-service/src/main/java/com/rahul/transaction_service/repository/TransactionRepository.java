package com.rahul.transaction_service.repository;

import com.rahul.transaction_service.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
           SELECT t FROM Transaction t
           WHERE t.fromWalletId = :walletId OR t.toWalletId = :walletId
           ORDER BY t.createdAt DESC
           """)
    Page<Transaction> findAllByWalletId(@Param("walletId") Long walletId, Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
            WHERE (t.fromWalletId = :walletId OR t.toWalletId = :walletId)
            ORDER BY t.createdAt DESC
            """)
    Page<Transaction> findHistory(@Param("walletId") Long walletId,
                                  Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
            WHERE (t.fromWalletId = :walletId OR t.toWalletId = :walletId)
              AND t.createdAt >= COALESCE(:fromDate, t.createdAt)
              AND t.createdAt <= COALESCE(:toDate, t.createdAt)
              AND t.amount >= COALESCE(:minAmount, t.amount)
              AND t.amount <= COALESCE(:maxAmount, t.amount)
            ORDER BY t.createdAt DESC
            """)
    Page<Transaction> findFilteredHistory(@Param("walletId") Long walletId,
                                          @Param("fromDate") LocalDateTime fromDate,
                                          @Param("toDate") LocalDateTime toDate,
                                          @Param("minAmount") BigDecimal minAmount,
                                          @Param("maxAmount") BigDecimal maxAmount,
                                          Pageable pageable);
}
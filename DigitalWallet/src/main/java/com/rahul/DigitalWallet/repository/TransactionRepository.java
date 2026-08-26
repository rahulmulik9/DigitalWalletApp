package com.rahul.DigitalWallet.repository;

import com.rahul.DigitalWallet.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
           SELECT t FROM Transaction t
           WHERE t.fromWallet.id = :walletId OR t.toWallet.id = :walletId
           ORDER BY t.createdAt DESC
           """)
    Page<Transaction> findAllByWalletId(@Param("walletId") Long walletId, Pageable pageable);

    @EntityGraph(attributePaths = {"fromWallet", "toWallet"})
    @Query("""
            SELECT t FROM Transaction t
            WHERE (t.fromWallet.id = :walletId OR t.toWallet.id = :walletId)
            ORDER BY t.createdAt DESC
            """)
    Page<Transaction> findHistory(@Param("walletId") Long walletId,
                                  Pageable pageable);
}

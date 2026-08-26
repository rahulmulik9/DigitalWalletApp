package com.rahul.DigitalWallet.repository;

import com.rahul.DigitalWallet.entity.LedgerEntry;
import com.rahul.DigitalWallet.entity.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN l.type = :credit THEN l.amount ELSE -l.amount END), 0)
            FROM LedgerEntry l WHERE l.wallet.id = :walletId
            """)
    BigDecimal calculateBalance(@Param("walletId") Long walletId, @Param("credit") LedgerEntryType credit);
}
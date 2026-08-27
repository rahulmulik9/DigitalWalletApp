package com.rahul.DigitalWallet.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // null for a DEPOSIT (money entering from outside the system)
    @ManyToOne
    @JoinColumn(name = "from_wallet_id")
    private Wallet fromWallet;

    // null for a WITHDRAW (money leaving the system)
    @ManyToOne
    @JoinColumn(name = "to_wallet_id")
    private Wallet toWallet;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    private String remarks;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.PERSIST)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<LedgerEntry> ledgerEntries = new ArrayList<>();

    public void addLedgerEntry(LedgerEntry entry) {
        ledgerEntries.add(entry);
    }
}

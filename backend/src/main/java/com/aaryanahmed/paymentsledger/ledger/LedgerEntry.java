package com.aaryanahmed.paymentsledger.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One side of a transfer as seen by one account: a negative amount for the sender,
 * a positive amount for the receiver. Entries are immutable.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long transferId;
    private Long accountId;
    private BigDecimal amount;
    private Instant createdAt;

    public LedgerEntry(Long transferId, Long accountId, BigDecimal amount) {
        this.transferId = transferId;
        this.accountId = accountId;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    protected LedgerEntry() {

    }

    public Long getId() {
        return id;
    }

    public Long getTransferId() {
        return transferId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

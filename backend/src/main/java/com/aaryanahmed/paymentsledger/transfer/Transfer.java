package com.aaryanahmed.paymentsledger.transfer;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A movement of money from one account to another. Transfers are immutable:
 * once written they are never updated or deleted, which is what makes the ledger an audit trail.
 */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private String currency;
    private Instant createdAt;

    // The id of the transfer this one undoes, or null for an ordinary transfer.
    private Long reversalOf;

    public Transfer(Long fromAccountId, Long toAccountId, BigDecimal amount, String currency, Long reversalOf) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.reversalOf = reversalOf;
        this.createdAt = Instant.now();
    }

    public boolean isReversal() {
        return reversalOf != null;
    }

    protected Transfer() {

    }

    public Long getId() {
        return id;
    }

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getReversalOf() {
        return reversalOf;
    }
}

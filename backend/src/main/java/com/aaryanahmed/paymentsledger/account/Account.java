package com.aaryanahmed.paymentsledger.account;


import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="accounts")
public class Account {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    private String ownerName;
    private String currency;
    private BigDecimal balance;
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    public Account(String ownerName, String currency) {
        this.ownerName = ownerName;
        this.currency = currency;
        this.balance = BigDecimal.ZERO;
        this.accountType = AccountType.CUSTOMER;
        this.createdAt = Instant.now();
    }

    /**
     * Takes money out of the account. CUSTOMER accounts can never go below zero;
     * EXTERNAL accounts can, because they represent money entering from outside the system.
     */
    public void debit(BigDecimal amount) {
        requirePositive(amount);
        BigDecimal newBalance = balance.subtract(amount);
        if (accountType == AccountType.CUSTOMER && newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException(id);
        }
        balance = newBalance;
    }

    /** Puts money into the account. */
    public void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    public AccountType getAccountType() {
        return accountType;
    }

    protected Account() {

    }

    public Long getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

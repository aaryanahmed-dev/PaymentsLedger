package com.aaryanahmed.paymentsledger.account;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(Long accountId) {
        super("Account with id " + accountId + " has insufficient funds");
    }
}

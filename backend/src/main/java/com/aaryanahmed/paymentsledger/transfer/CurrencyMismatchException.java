package com.aaryanahmed.paymentsledger.transfer;

public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(String fromCurrency, String toCurrency) {
        super("Cannot transfer between accounts in different currencies (" + fromCurrency + " and " + toCurrency + ")");
    }
}

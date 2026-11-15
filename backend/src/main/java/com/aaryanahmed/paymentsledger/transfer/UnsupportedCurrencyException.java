package com.aaryanahmed.paymentsledger.transfer;

public class UnsupportedCurrencyException extends RuntimeException {
    public UnsupportedCurrencyException(String currency) {
        super("Deposits are not supported for currency " + currency);
    }
}

package com.aaryanahmed.paymentsledger.transfer;

public class InvalidTransferException extends RuntimeException {
    public InvalidTransferException(String message) {
        super(message);
    }
}

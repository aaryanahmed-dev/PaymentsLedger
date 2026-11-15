package com.aaryanahmed.paymentsledger.transfer;

public class TransferNotReversibleException extends RuntimeException {
    public TransferNotReversibleException(String message) {
        super(message);
    }
}

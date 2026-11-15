package com.aaryanahmed.paymentsledger.transfer;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(Long id) {
        super("Transfer with id " + id + " not found");
    }
}

package com.aaryanahmed.paymentsledger.idempotency;

public class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key must be between 1 and " + IdempotencyService.MAX_KEY_LENGTH + " characters");
    }
}

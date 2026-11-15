package com.aaryanahmed.paymentsledger.webhook;

public enum OutboxStatus {
    /** Waiting to be sent, or waiting for its next retry. */
    PENDING,
    /** The endpoint answered with a 2xx status. */
    DELIVERED,
    /** Every attempt failed; the dispatcher has given up. */
    FAILED
}

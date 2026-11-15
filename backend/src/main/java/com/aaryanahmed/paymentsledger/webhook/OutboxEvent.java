package com.aaryanahmed.paymentsledger.webhook;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/** A webhook waiting to be delivered to one endpoint, with its retry state. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long webhookEndpointId;
    private String eventType;
    private String payload;
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant createdAt;
    private Instant deliveredAt;

    public OutboxEvent(Long webhookEndpointId, String eventType, String payload) {
        this.webhookEndpointId = webhookEndpointId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;   // due immediately
    }

    protected OutboxEvent() {

    }

    public void markDelivered(Instant now) {
        attempts++;
        status = OutboxStatus.DELIVERED;
        deliveredAt = now;
        lastError = null;
    }

    /**
     * Records a failed attempt. Until {@code maxAttempts} is reached the event stays PENDING
     * and the wait before the next try doubles each time (exponential backoff):
     * baseDelay, 2 x baseDelay, 4 x baseDelay, ...
     */
    public void markAttemptFailed(String error, Instant now, int maxAttempts, Duration baseDelay) {
        attempts++;
        lastError = error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        if (attempts >= maxAttempts) {
            status = OutboxStatus.FAILED;
        } else {
            nextAttemptAt = now.plus(baseDelay.multipliedBy(1L << (attempts - 1)));
        }
    }

    public Long getId() {
        return id;
    }

    public Long getWebhookEndpointId() {
        return webhookEndpointId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}

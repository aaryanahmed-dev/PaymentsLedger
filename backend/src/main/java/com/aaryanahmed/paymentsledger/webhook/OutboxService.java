package com.aaryanahmed.paymentsledger.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class OutboxService {

    private final OutboxEventRepository eventRepo;
    private final WebhookEndpointRepository endpointRepo;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final Duration retryBaseDelay;

    public OutboxService(OutboxEventRepository eventRepo,
                         WebhookEndpointRepository endpointRepo,
                         ObjectMapper objectMapper,
                         @Value("${webhooks.max-attempts}") int maxAttempts,
                         @Value("${webhooks.retry-base-delay-ms}") long retryBaseDelayMs) {
        this.eventRepo = eventRepo;
        this.endpointRepo = endpointRepo;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelay = Duration.ofMillis(retryBaseDelayMs);
    }

    /**
     * Queues an event for every registered endpoint. Nothing is sent here: rows are only
     * written to the outbox table.
     *
     * Propagation.MANDATORY means "there must already be a transaction; fail otherwise".
     * That enforces the whole point of the outbox pattern: the event is saved in the same
     * transaction as the business change, so they commit or roll back together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String eventType, Object data) {
        List<WebhookEndpoint> endpoints = endpointRepo.findAll();
        if (endpoints.isEmpty()) {
            return;
        }
        String payload = objectMapper.writeValueAsString(new WebhookPayload(eventType, data));
        for (WebhookEndpoint endpoint : endpoints) {
            eventRepo.save(new OutboxEvent(endpoint.getId(), eventType, payload));
        }
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> findDue(Instant now) {
        return eventRepo.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderById(OutboxStatus.PENDING, now);
    }

    @Transactional
    public void recordDelivered(Long eventId) {
        eventRepo.findById(eventId).orElseThrow().markDelivered(Instant.now());
    }

    @Transactional
    public void recordFailure(Long eventId, String error) {
        eventRepo.findById(eventId).orElseThrow()
                .markAttemptFailed(error, Instant.now(), maxAttempts, retryBaseDelay);
    }

    /** The JSON body sent to the endpoint. */
    record WebhookPayload(String type, Object data) {
    }
}

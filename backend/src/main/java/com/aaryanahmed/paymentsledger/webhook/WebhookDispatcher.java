package com.aaryanahmed.paymentsledger.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Sends the events waiting in the outbox table. Runs on a timer, separately from the
 * requests that created the events, so a slow or broken receiver never slows down a transfer.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final OutboxService outboxService;
    private final WebhookEndpointRepository endpointRepo;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public WebhookDispatcher(OutboxService outboxService, WebhookEndpointRepository endpointRepo) {
        this.outboxService = outboxService;
        this.endpointRepo = endpointRepo;
    }

    // fixedDelay: wait this long after one run finishes before starting the next,
    // so two runs never overlap.
    @Scheduled(fixedDelayString = "${webhooks.dispatch-interval-ms}")
    public void dispatchPending() {
        for (OutboxEvent event : outboxService.findDue(Instant.now())) {
            deliver(event);
        }
    }

    // Deliberately NOT @Transactional: no database transaction (or row lock) is held open
    // while waiting for somebody else's server. The result is saved in its own short transaction.
    private void deliver(OutboxEvent event) {
        WebhookEndpoint endpoint = endpointRepo.findById(event.getWebhookEndpointId()).orElseThrow();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint.getUrl()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Event-Id", String.valueOf(event.getId()))
                    .header(WebhookSigner.SIGNATURE_HEADER, WebhookSigner.signatureHeader(
                            endpoint.getSecret(), Instant.now().getEpochSecond(), event.getPayload()))
                    .POST(HttpRequest.BodyPublishers.ofString(event.getPayload()))
                    .build();
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                outboxService.recordDelivered(event.getId());
            } else {
                failed(event, "HTTP " + status);
            }
        } catch (IOException | IllegalArgumentException e) {
            // Some network exceptions (e.g. connection refused) carry no message.
            String reason = e.getMessage() == null ? "" : ": " + e.getMessage();
            failed(event, e.getClass().getSimpleName() + reason);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // the application is shutting down; try again next start
        }
    }

    private void failed(OutboxEvent event, String error) {
        log.warn("Webhook event {} to endpoint {} failed: {}", event.getId(), event.getWebhookEndpointId(), error);
        outboxService.recordFailure(event.getId(), error);
    }
}

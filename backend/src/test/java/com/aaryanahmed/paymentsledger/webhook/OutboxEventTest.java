package com.aaryanahmed.paymentsledger.webhook;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration BASE_DELAY = Duration.ofSeconds(2);
    private static final int MAX_ATTEMPTS = 5;

    @Test
    void newEventIsPendingAndDueImmediately() {
        OutboxEvent event = new OutboxEvent(1L, "transfer.created", "{}");

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getNextAttemptAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void retryDelayDoublesAfterEachFailure() {
        OutboxEvent event = new OutboxEvent(1L, "transfer.created", "{}");

        event.markAttemptFailed("HTTP 500", NOW, MAX_ATTEMPTS, BASE_DELAY);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));

        event.markAttemptFailed("HTTP 500", NOW, MAX_ATTEMPTS, BASE_DELAY);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(4));

        event.markAttemptFailed("HTTP 500", NOW, MAX_ATTEMPTS, BASE_DELAY);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(8));

        event.markAttemptFailed("HTTP 500", NOW, MAX_ATTEMPTS, BASE_DELAY);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(16));
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(4);
    }

    @Test
    void eventFailsPermanentlyAfterTheLastAttempt() {
        OutboxEvent event = new OutboxEvent(1L, "transfer.created", "{}");

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            event.markAttemptFailed("HTTP 503", NOW, MAX_ATTEMPTS, BASE_DELAY);
        }

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(event.getLastError()).isEqualTo("HTTP 503");
    }

    @Test
    void deliveryAfterAFailureClearsTheError() {
        OutboxEvent event = new OutboxEvent(1L, "transfer.created", "{}");
        event.markAttemptFailed("HTTP 500", NOW, MAX_ATTEMPTS, BASE_DELAY);

        event.markDelivered(NOW);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DELIVERED);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getDeliveredAt()).isEqualTo(NOW);
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void longErrorMessagesAreTruncatedToFitTheColumn() {
        OutboxEvent event = new OutboxEvent(1L, "transfer.created", "{}");

        event.markAttemptFailed("x".repeat(2000), NOW, MAX_ATTEMPTS, BASE_DELAY);

        assertThat(event.getLastError()).hasSize(500);
    }
}

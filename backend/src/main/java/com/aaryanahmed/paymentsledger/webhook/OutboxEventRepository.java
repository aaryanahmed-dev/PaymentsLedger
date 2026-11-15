package com.aaryanahmed.paymentsledger.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // "Top100" limits the batch, so one dispatcher run never loads an unbounded number of rows.
    List<OutboxEvent> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderById(OutboxStatus status, Instant now);
}

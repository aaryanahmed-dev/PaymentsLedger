package com.aaryanahmed.paymentsledger.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {
}

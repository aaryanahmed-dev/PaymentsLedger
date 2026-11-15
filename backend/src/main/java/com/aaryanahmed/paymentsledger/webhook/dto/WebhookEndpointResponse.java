package com.aaryanahmed.paymentsledger.webhook.dto;

import com.aaryanahmed.paymentsledger.webhook.WebhookEndpoint;

import java.time.Instant;

/** Deliberately has no secret field: the secret is only shown once, when the endpoint is created. */
public record WebhookEndpointResponse(Long id,
                                      String url,
                                      Instant createdAt
){
    public static WebhookEndpointResponse from(WebhookEndpoint endpoint) {
        return new WebhookEndpointResponse(endpoint.getId(), endpoint.getUrl(), endpoint.getCreatedAt());
    }
}

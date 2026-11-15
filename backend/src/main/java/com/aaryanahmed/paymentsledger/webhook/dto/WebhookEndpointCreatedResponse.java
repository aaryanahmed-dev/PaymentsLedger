package com.aaryanahmed.paymentsledger.webhook.dto;

import com.aaryanahmed.paymentsledger.webhook.WebhookEndpoint;

import java.time.Instant;

public record WebhookEndpointCreatedResponse(Long id,
                                             String url,
                                             String secret,
                                             Instant createdAt
){
    public static WebhookEndpointCreatedResponse from(WebhookEndpoint endpoint) {
        return new WebhookEndpointCreatedResponse(
                endpoint.getId(), endpoint.getUrl(), endpoint.getSecret(), endpoint.getCreatedAt());
    }
}

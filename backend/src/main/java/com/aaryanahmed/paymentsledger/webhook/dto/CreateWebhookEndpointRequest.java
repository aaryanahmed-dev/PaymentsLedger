package com.aaryanahmed.paymentsledger.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWebhookEndpointRequest(@Schema(example = "https://example.com/webhooks/ledger")
                                           @NotBlank @Size(max = 500)
                                           @Pattern(regexp = "https?://\\S+", message = "must be an http or https URL")
                                           String url)
{ }

package com.aaryanahmed.paymentsledger.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWebhookEndpointRequest(@NotBlank @Size(max = 500)
                                           @Pattern(regexp = "https?://\\S+", message = "must be an http or https URL")
                                           String url)
{ }

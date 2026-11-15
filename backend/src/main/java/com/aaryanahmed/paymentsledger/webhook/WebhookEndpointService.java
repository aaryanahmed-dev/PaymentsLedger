package com.aaryanahmed.paymentsledger.webhook;

import com.aaryanahmed.paymentsledger.webhook.dto.CreateWebhookEndpointRequest;
import com.aaryanahmed.paymentsledger.webhook.dto.WebhookEndpointCreatedResponse;
import com.aaryanahmed.paymentsledger.webhook.dto.WebhookEndpointResponse;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

@Service
public class WebhookEndpointService {

    private final WebhookEndpointRepository endpointRepo;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookEndpointService(WebhookEndpointRepository endpointRepo) {
        this.endpointRepo = endpointRepo;
    }

    public WebhookEndpointCreatedResponse createEndpoint(CreateWebhookEndpointRequest request) {
        WebhookEndpoint saved = endpointRepo.save(new WebhookEndpoint(request.url(), generateSecret()));
        return WebhookEndpointCreatedResponse.from(saved);
    }

    public List<WebhookEndpointResponse> getAllEndpoints() {
        return endpointRepo.findAll().stream().map(WebhookEndpointResponse::from).toList();
    }

    // The server picks the secret so it is always long and random: 32 random bytes as hex.
    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }
}

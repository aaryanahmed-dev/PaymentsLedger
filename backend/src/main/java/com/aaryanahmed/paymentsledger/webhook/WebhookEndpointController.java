package com.aaryanahmed.paymentsledger.webhook;

import com.aaryanahmed.paymentsledger.webhook.dto.CreateWebhookEndpointRequest;
import com.aaryanahmed.paymentsledger.webhook.dto.WebhookEndpointCreatedResponse;
import com.aaryanahmed.paymentsledger.webhook.dto.WebhookEndpointResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class WebhookEndpointController {

    private final WebhookEndpointService endpointService;

    public WebhookEndpointController(WebhookEndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @PostMapping("/webhook-endpoints")
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookEndpointCreatedResponse createEndpoint(@RequestBody @Valid CreateWebhookEndpointRequest request) {
        return endpointService.createEndpoint(request);
    }

    @GetMapping("/webhook-endpoints")
    public List<WebhookEndpointResponse> getEndpoints() {
        return endpointService.getAllEndpoints();
    }
}

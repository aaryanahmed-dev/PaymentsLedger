package com.aaryanahmed.paymentsledger.webhook;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A URL that wants to be told about events, and the secret used to sign what is sent to it. */
@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String url;
    private String secret;
    private Instant createdAt;

    public WebhookEndpoint(String url, String secret) {
        this.url = url;
        this.secret = secret;
        this.createdAt = Instant.now();
    }

    protected WebhookEndpoint() {

    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getSecret() {
        return secret;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

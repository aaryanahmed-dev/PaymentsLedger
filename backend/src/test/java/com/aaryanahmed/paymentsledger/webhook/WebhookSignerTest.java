package com.aaryanahmed.paymentsledger.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    @Test
    void matchesAValueComputedIndependently() {
        // Expected value computed outside Java, with Python:
        //   hmac.new(b'whsec_test', b'1700000000.{"type":"transfer.created"}', hashlib.sha256).hexdigest()
        // This proves a receiver written in another language gets the same signature.
        assertThat(WebhookSigner.sign("whsec_test", 1700000000L, "{\"type\":\"transfer.created\"}"))
                .isEqualTo("eae3037eeec8b0fad1a9a7ef8d6d7ba0b023c574d3353b32ab8188828bdc53dc");
    }

    @Test
    void headerContainsTimestampAndSignature() {
        String header = WebhookSigner.signatureHeader("secret", 1700000000L, "{\"a\":1}");

        assertThat(header).matches("t=1700000000,v1=[0-9a-f]{64}");
        assertThat(header).endsWith(WebhookSigner.sign("secret", 1700000000L, "{\"a\":1}"));
    }

    @Test
    void signatureChangesWithSecretTimestampOrBody() {
        String signature = WebhookSigner.sign("secret", 1700000000L, "body");

        assertThat(WebhookSigner.sign("other-secret", 1700000000L, "body")).isNotEqualTo(signature);
        assertThat(WebhookSigner.sign("secret", 1700000001L, "body")).isNotEqualTo(signature);
        assertThat(WebhookSigner.sign("secret", 1700000000L, "tampered")).isNotEqualTo(signature);
    }
}

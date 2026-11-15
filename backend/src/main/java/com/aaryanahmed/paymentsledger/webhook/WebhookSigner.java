package com.aaryanahmed.paymentsledger.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Signs webhook requests so the receiver can check that they really came from this
 * service and were not altered.
 *
 * The signature is HMAC-SHA256(secret, timestamp + "." + body), sent as the header
 *   X-Webhook-Signature: t=<unix seconds>,v1=<hex signature>
 *
 * Including the timestamp in the signed text lets the receiver reject old requests,
 * so a captured request cannot be replayed later.
 */
public final class WebhookSigner {

    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSigner() {
    }

    public static String signatureHeader(String secret, long timestampSeconds, String body) {
        return "t=" + timestampSeconds + ",v1=" + sign(secret, timestampSeconds, body);
    }

    static String sign(String secret, long timestampSeconds, String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] signature = mac.doFinal((timestampSeconds + "." + body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);   // every Java runtime is required to provide HmacSHA256
        }
    }
}

package com.aaryanahmed.paymentsledger.webhook;

import com.aaryanahmed.paymentsledger.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webhook delivery against MockWebServer, a real HTTP server on localhost that plays the
 * part of the customer's system. Scheduling is off in tests, so each test calls
 * dispatchPending() itself: one call is exactly one run of the dispatcher.
 */
class WebhookTest extends IntegrationTest {

    @Autowired
    private WebhookDispatcher dispatcher;

    private MockWebServer receiver;
    private long endpointId;
    private String secret;
    private long alice;
    private long bob;

    @BeforeEach
    void setUp() throws Exception {
        // Accounts are funded BEFORE the endpoint is registered, so setup creates no events.
        alice = createAccount("Alice", "EUR");
        bob = createAccount("Bob", "EUR");
        deposit(alice, "100.00").andExpect(status().isCreated());

        receiver = new MockWebServer();
        receiver.start();
        String json = registerEndpoint(receiver.url("/hooks").toString())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        endpointId = ((Number) JsonPath.read(json, "$.id")).longValue();
        secret = JsonPath.read(json, "$.secret");
    }

    @AfterEach
    void tearDown() throws Exception {
        receiver.shutdown();
        // Remove the endpoint so transfers made by other test classes do not queue events for it.
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM webhook_endpoints");
    }

    // ---- endpoint registration ----

    @Test
    void secretIsReturnedOnCreationButNeverListed() throws Exception {
        assertThat(secret).startsWith("whsec_").hasSizeGreaterThan(40);

        mockMvc.perform(get("/webhook-endpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(endpointId))
                .andExpect(jsonPath("$[0].url").exists())
                .andExpect(jsonPath("$[0].secret").doesNotExist());
    }

    @Test
    void invalidUrlIs400() throws Exception {
        registerEndpoint("not-a-url").andExpect(status().isBadRequest());
        registerEndpoint("").andExpect(status().isBadRequest());
    }

    // ---- outbox: events are written with the transfer ----

    @Test
    void transferWritesOnePendingEvent() throws Exception {
        long transferId = idOf(transfer(alice, bob, "10.00"));

        Map<String, Object> event = onlyEvent();
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("event_type")).isEqualTo("transfer.created");
        assertThat(((Number) JsonPath.read((String) event.get("payload"), "$.data.id")).longValue())
                .isEqualTo(transferId);
        assertThat(receiver.getRequestCount()).isZero();   // nothing is sent until the dispatcher runs
    }

    @Test
    void rejectedTransferWritesNoEvent() throws Exception {
        transfer(alice, bob, "999.00").andExpect(status().isUnprocessableEntity());

        assertThat(eventCount()).isZero();
    }

    @Test
    void repeatedIdempotentRequestWritesOneEvent() throws Exception {
        transfer(alice, bob, "10.00", "webhook-idempotency-key").andExpect(status().isCreated());
        transfer(alice, bob, "10.00", "webhook-idempotency-key").andExpect(status().isCreated());

        assertThat(eventCount()).isEqualTo(1);
    }

    // ---- delivery ----

    @Test
    void deliversEventWithAVerifiableSignature() throws Exception {
        receiver.enqueue(new MockResponse().setResponseCode(200));
        long transferId = idOf(transfer(alice, bob, "10.00"));

        dispatcher.dispatchPending();

        RecordedRequest request = receiver.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        String body = request.getBody().readUtf8();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");
        assertThat(request.getHeader("X-Webhook-Event-Id")).isEqualTo(String.valueOf(onlyEvent().get("id")));
        assertThat((String) JsonPath.read(body, "$.type")).isEqualTo("transfer.created");
        assertThat(((Number) JsonPath.read(body, "$.data.id")).longValue()).isEqualTo(transferId);
        assertThat(((Number) JsonPath.read(body, "$.data.fromAccountId")).longValue()).isEqualTo(alice);

        // Verify the signature the way a receiver would: recompute it from the secret,
        // the timestamp in the header and the exact body received.
        Matcher header = Pattern.compile("t=(\\d+),v1=([0-9a-f]{64})")
                .matcher(request.getHeader("X-Webhook-Signature"));
        assertThat(header.matches()).isTrue();
        long timestamp = Long.parseLong(header.group(1));
        assertThat(header.group(2)).isEqualTo(hmacSha256(secret, timestamp + "." + body));
        assertThat(header.group(2)).isNotEqualTo(hmacSha256("wrong-secret", timestamp + "." + body));
        assertThat(timestamp).isBetween(Instant.now().getEpochSecond() - 60, Instant.now().getEpochSecond());

        Map<String, Object> event = onlyEvent();
        assertThat(event.get("status")).isEqualTo("DELIVERED");
        assertThat(event.get("attempts")).isEqualTo(1);
        assertThat(event.get("delivered_at")).isNotNull();
    }

    @Test
    void deliveredEventIsNotSentAgain() throws Exception {
        receiver.enqueue(new MockResponse().setResponseCode(204));
        transfer(alice, bob, "10.00");

        dispatcher.dispatchPending();
        dispatcher.dispatchPending();

        assertThat(receiver.getRequestCount()).isEqualTo(1);
    }

    @Test
    void reversalSendsATransferReversedEvent() throws Exception {
        receiver.enqueue(new MockResponse().setResponseCode(200));
        receiver.enqueue(new MockResponse().setResponseCode(200));
        long original = idOf(transfer(alice, bob, "10.00"));
        reverse(original).andExpect(status().isCreated());

        dispatcher.dispatchPending();

        assertThat((String) JsonPath.read(receiver.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8(), "$.type"))
                .isEqualTo("transfer.created");
        String reversed = receiver.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8();
        assertThat((String) JsonPath.read(reversed, "$.type")).isEqualTo("transfer.reversed");
        assertThat(((Number) JsonPath.read(reversed, "$.data.reversalOf")).longValue()).isEqualTo(original);
    }

    // ---- retries ----

    @Test
    void failedDeliveryIsRetriedAfterTheBackoffDelay() throws Exception {
        receiver.enqueue(new MockResponse().setResponseCode(500));
        receiver.enqueue(new MockResponse().setResponseCode(200));
        transfer(alice, bob, "10.00");

        dispatcher.dispatchPending();

        Map<String, Object> afterFailure = onlyEvent();
        assertThat(afterFailure.get("status")).isEqualTo("PENDING");
        assertThat(afterFailure.get("attempts")).isEqualTo(1);
        assertThat(afterFailure.get("last_error")).isEqualTo("HTTP 500");

        // The retry is scheduled for later, so running the dispatcher again now sends nothing.
        dispatcher.dispatchPending();
        assertThat(receiver.getRequestCount()).isEqualTo(1);

        // Once the delay has passed, the event is sent again and succeeds.
        makeEventsDueNow();
        dispatcher.dispatchPending();

        assertThat(receiver.getRequestCount()).isEqualTo(2);
        Map<String, Object> afterRetry = onlyEvent();
        assertThat(afterRetry.get("status")).isEqualTo("DELIVERED");
        assertThat(afterRetry.get("attempts")).isEqualTo(2);
        assertThat(afterRetry.get("last_error")).isNull();
    }

    @Test
    void eventIsMarkedFailedAfterTheMaximumNumberOfAttempts() throws Exception {
        transfer(alice, bob, "10.00");

        for (int attempt = 1; attempt <= 5; attempt++) {
            receiver.enqueue(new MockResponse().setResponseCode(503));
            makeEventsDueNow();
            dispatcher.dispatchPending();
        }

        Map<String, Object> event = onlyEvent();
        assertThat(event.get("status")).isEqualTo("FAILED");
        assertThat(event.get("attempts")).isEqualTo(5);
        assertThat(event.get("last_error")).isEqualTo("HTTP 503");

        // A FAILED event is dead: the dispatcher never picks it up again.
        makeEventsDueNow();
        dispatcher.dispatchPending();
        assertThat(receiver.getRequestCount()).isEqualTo(5);
    }

    @Test
    void unreachableEndpointCountsAsAFailedAttempt() throws Exception {
        transfer(alice, bob, "10.00");
        receiver.shutdown();   // nothing is listening on that port any more

        dispatcher.dispatchPending();

        Map<String, Object> event = onlyEvent();
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("attempts")).isEqualTo(1);
        assertThat((String) event.get("last_error")).isNotBlank();
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions registerEndpoint(String url) throws Exception {
        return mockMvc.perform(post("/webhook-endpoints").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"url": "%s"}
                        """.formatted(url)));
    }

    private Map<String, Object> onlyEvent() {
        return jdbc.queryForMap("SELECT * FROM outbox_events WHERE webhook_endpoint_id = ?", endpointId);
    }

    private int eventCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE webhook_endpoint_id = ?", Integer.class, endpointId);
    }

    /** Simulates time passing: pulls every scheduled retry back into the past. */
    private void makeEventsDueNow() {
        jdbc.update("UPDATE outbox_events SET next_attempt_at = '2000-01-01 00:00:00'");
    }

    private static String hmacSha256(String secret, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }
}

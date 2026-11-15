package com.aaryanahmed.paymentsledger.idempotency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyKeyRepository keyRepo;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository keyRepo, ObjectMapper objectMapper) {
        this.keyRepo = keyRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs {@code action} at most once per key.
     *
     * The key row and whatever the action writes are saved in the SAME transaction, so there
     * is never a key without its transfer or a transfer without its key. If the action throws,
     * everything rolls back, including the key, so a failed request can be retried with the same key.
     *
     * @param request a description of the request; it is hashed so that reusing a key
     *                for a different request can be detected
     */
    @Transactional
    public <T> T execute(String key, String request, Class<T> responseType, Supplier<T> action) {
        if (key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new InvalidIdempotencyKeyException();
        }
        String requestHash = sha256(request);

        // This must be the first statement of the transaction (see LEARNING_NOTES, Phase 4).
        boolean firstTimeSeen = keyRepo.insertIfAbsent(key, requestHash) == 1;
        IdempotencyKey stored = keyRepo.findByIdempotencyKey(key).orElseThrow();

        if (!firstTimeSeen) {
            if (!stored.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyConflictException();
            }
            // A repeat of a request we already completed: replay the original response.
            return objectMapper.readValue(stored.getResponseBody(), responseType);
        }

        T response = action.get();
        stored.recordResponse(objectMapper.writeValueAsString(response));
        return response;
    }

    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // every Java runtime is required to provide SHA-256
        }
    }
}

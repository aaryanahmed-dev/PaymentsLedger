package com.aaryanahmed.paymentsledger.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    /**
     * Claims the key. Returns 1 if this call inserted it, 0 if the key already existed.
     *
     * INSERT IGNORE is MySQL syntax: on a duplicate key it skips the row instead of failing.
     * If another transaction has inserted the same key but not committed yet, this statement
     * WAITS for it to finish, which is how simultaneous duplicate requests are serialised.
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO idempotency_keys (idempotency_key, request_hash) VALUES (:key, :requestHash)",
            nativeQuery = true)
    int insertIfAbsent(@Param("key") String key, @Param("requestHash") String requestHash);

    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);
}

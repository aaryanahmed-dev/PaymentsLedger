CREATE TABLE idempotency_keys (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotency_key  VARCHAR(255)  NOT NULL,
    request_hash     CHAR(64)      NOT NULL,
    response_body    VARCHAR(2000) NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- The unique constraint is what guarantees one transfer per key, even for simultaneous requests.
    CONSTRAINT uq_idempotency_keys_key UNIQUE (idempotency_key)
);

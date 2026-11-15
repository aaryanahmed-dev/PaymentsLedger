CREATE TABLE webhook_endpoints (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    url         VARCHAR(500) NOT NULL,
    secret      VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

-- One row per (event, endpoint) that still has to be, or has been, delivered.
CREATE TABLE outbox_events (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    webhook_endpoint_id  BIGINT        NOT NULL,
    event_type           VARCHAR(50)   NOT NULL,
    payload              VARCHAR(2000) NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    attempts             INT           NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMP(6)  NOT NULL,
    last_error           VARCHAR(500)  NULL,
    created_at           TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    delivered_at         TIMESTAMP(6)  NULL,

    CONSTRAINT fk_outbox_endpoint FOREIGN KEY (webhook_endpoint_id) REFERENCES webhook_endpoints (id),

    -- The dispatcher's query: pending events that are due, oldest first.
    INDEX idx_outbox_status_next_attempt (status, next_attempt_at)
);

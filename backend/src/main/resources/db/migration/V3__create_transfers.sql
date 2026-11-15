CREATE TABLE transfers (
                           id               BIGINT PRIMARY KEY AUTO_INCREMENT,
                           from_account_id  BIGINT        NOT NULL,
                           to_account_id    BIGINT        NOT NULL,
                           amount           DECIMAL(19,4) NOT NULL,
                           currency         CHAR(3)       NOT NULL,
                           created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           CONSTRAINT fk_transfers_from FOREIGN KEY (from_account_id) REFERENCES accounts (id),
                           CONSTRAINT fk_transfers_to   FOREIGN KEY (to_account_id)   REFERENCES accounts (id),
                           CONSTRAINT chk_transfers_amount_positive CHECK (amount > 0)
);
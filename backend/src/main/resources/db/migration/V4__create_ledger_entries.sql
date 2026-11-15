CREATE TABLE ledger_entries (
                                id           BIGINT PRIMARY KEY AUTO_INCREMENT,
                                transfer_id  BIGINT        NOT NULL,
                                account_id   BIGINT        NOT NULL,
                                amount       DECIMAL(19,4) NOT NULL,
                                created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_entries_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id),
                                CONSTRAINT fk_entries_account  FOREIGN KEY (account_id)  REFERENCES accounts (id),
                                CONSTRAINT chk_entries_amount_nonzero CHECK (amount <> 0),

                                INDEX idx_entries_account_created (account_id, created_at)
);
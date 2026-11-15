ALTER TABLE transfers
    ADD COLUMN reversal_of BIGINT NULL,
    ADD CONSTRAINT fk_transfers_reversal_of FOREIGN KEY (reversal_of) REFERENCES transfers (id),
    -- A unique index allows many NULLs but only one row per non-NULL value,
    -- so the database itself guarantees a transfer is reversed at most once.
    ADD CONSTRAINT uq_transfers_reversal_of UNIQUE (reversal_of);

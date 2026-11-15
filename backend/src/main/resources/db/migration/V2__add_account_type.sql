ALTER TABLE accounts
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';

INSERT INTO accounts (owner_name, currency, balance, account_type)
VALUES ('External funding (EUR)', 'EUR', 0, 'EXTERNAL');
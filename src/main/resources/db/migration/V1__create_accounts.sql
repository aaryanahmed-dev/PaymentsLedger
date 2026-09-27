CREATE TABLE accounts (
                          id          BIGINT PRIMARY KEY AUTO_INCREMENT,
                          owner_name  VARCHAR(100)   NOT NULL,
                          currency    CHAR(3)        NOT NULL,
                          balance     DECIMAL(19,4)  NOT NULL DEFAULT 0,
                          created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
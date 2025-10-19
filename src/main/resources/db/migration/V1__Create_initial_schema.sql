CREATE TABLE users
(
    id         VARCHAR(100) PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE wallets
(
    id      VARCHAR(80) PRIMARY KEY,
    user_id VARCHAR(80) NOT NULL,
    balance BIGINT      NOT NULL,
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE transactions
(
    id                    VARCHAR(100) PRIMARY KEY,
    wallet_id             VARCHAR(100) NOT NULL,
    user_id               VARCHAR(100) NOT NULL,
    destination_wallet_id VARCHAR(100), -- null if not a transfer
    destination_user_id   VARCHAR(100), -- null if not a transfer
    amount                BIGINT       NOT NULL,
    type                  VARCHAR(50)  NOT NULL,
    status                VARCHAR(50)  NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_dest_wallet FOREIGN KEY (destination_wallet_id) REFERENCES wallets (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_dest_user FOREIGN KEY (destination_user_id) REFERENCES users (id) ON DELETE CASCADE
);

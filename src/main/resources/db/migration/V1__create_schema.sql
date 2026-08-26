-- Schema inicial da Banking API.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(180) NOT NULL,
    document      VARCHAR(14)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email    UNIQUE (email),
    CONSTRAINT uk_users_document UNIQUE (document),
    CONSTRAINT ck_users_role     CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE accounts (
    id             UUID          PRIMARY KEY,
    account_number VARCHAR(20)   NOT NULL,
    owner_id       UUID          NOT NULL,
    type           VARCHAR(20)   NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    balance        NUMERIC(19,2) NOT NULL DEFAULT 0,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_accounts_number   UNIQUE (account_number),
    CONSTRAINT fk_accounts_owner    FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT ck_accounts_type     CHECK (type IN ('CHECKING', 'SAVINGS')),
    CONSTRAINT ck_accounts_status   CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED')),
    -- Invariante do dominio: nenhuma conta pode ficar com saldo negativo.
    CONSTRAINT ck_accounts_balance  CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_owner ON accounts (owner_id);

CREATE TABLE transactions (
    id                      UUID          PRIMARY KEY,
    operation_id            UUID          NOT NULL,
    account_id              UUID          NOT NULL,
    counterparty_account_id UUID,
    type                    VARCHAR(20)   NOT NULL,
    amount                  NUMERIC(19,2) NOT NULL,
    balance_after           NUMERIC(19,2) NOT NULL,
    description             VARCHAR(255),
    created_at              TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_transactions_account      FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_counterparty FOREIGN KEY (counterparty_account_id) REFERENCES accounts (id),
    CONSTRAINT ck_transactions_type         CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_OUT', 'TRANSFER_IN')),
    CONSTRAINT ck_transactions_amount       CHECK (amount > 0)
);

-- Suporta a consulta de extrato: filtro por conta, ordenado por data desc.
CREATE INDEX idx_transactions_account_date ON transactions (account_id, created_at DESC);
CREATE INDEX idx_transactions_operation    ON transactions (operation_id);

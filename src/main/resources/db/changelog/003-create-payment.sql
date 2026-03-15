--liquibase formatted sql

--changeset starter:003-create-payment
CREATE TABLE payment (
    id              BIGSERIAL PRIMARY KEY,
    drawdown_id     BIGINT         NOT NULL REFERENCES drawdown(id),
    bank            VARCHAR(50)    NOT NULL,
    transfer_mode   VARCHAR(20)    NOT NULL,
    amount          NUMERIC(15,2)  NOT NULL,
    status          VARCHAR(50)    NOT NULL DEFAULT 'INITIATED',
    bank_reference  VARCHAR(255),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

--changeset starter:003-create-payment-aud
CREATE TABLE payment_aud (
    id              BIGINT       NOT NULL,
    rev             INTEGER      NOT NULL REFERENCES revinfo(rev),
    revtype         SMALLINT,
    drawdown_id     BIGINT,
    bank            VARCHAR(50),
    transfer_mode   VARCHAR(20),
    amount          NUMERIC(15,2),
    status          VARCHAR(50),
    bank_reference  VARCHAR(255),
    PRIMARY KEY (id, rev)
);

--changeset starter:003-create-outbox-event
CREATE TABLE outbox_event (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(100)   NOT NULL,
    aggregate_id    BIGINT         NOT NULL,
    event_type      VARCHAR(100)   NOT NULL,
    payload         TEXT,
    status          VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_event_pending ON outbox_event (status, event_type)
    WHERE status = 'PENDING';

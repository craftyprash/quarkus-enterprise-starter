--liquibase formatted sql

--changeset starter:002-create-drawdown
CREATE TABLE drawdown (
    id             BIGSERIAL PRIMARY KEY,
    applicant_id   BIGINT         NOT NULL REFERENCES applicant(id),
    anchor_code    VARCHAR(50)    NOT NULL,
    amount         NUMERIC(15,2)  NOT NULL,
    status         VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

--changeset starter:002-create-drawdown-aud
CREATE TABLE drawdown_aud (
    id             BIGINT       NOT NULL,
    rev            INTEGER      NOT NULL REFERENCES revinfo(rev),
    revtype        SMALLINT,
    applicant_id   BIGINT,
    anchor_code    VARCHAR(50),
    amount         NUMERIC(15,2),
    status         VARCHAR(50),
    PRIMARY KEY (id, rev)
);

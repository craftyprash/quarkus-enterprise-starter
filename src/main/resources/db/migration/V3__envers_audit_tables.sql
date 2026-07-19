-- Hibernate Envers audit tables (payment and drawdown are @Audited).
-- Flyway owns these because Hibernate does not create Envers tables under generation=none.

CREATE SEQUENCE revinfo_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE revinfo (
    rev      INTEGER NOT NULL,
    revtstmp BIGINT,
    PRIMARY KEY (rev)
);

CREATE TABLE drawdown_aud (
    rev          INTEGER       NOT NULL,
    revtype      SMALLINT,
    id           BIGINT        NOT NULL,
    applicant_id BIGINT,
    anchor_code  VARCHAR(255),
    amount       NUMERIC(15,2),
    status       VARCHAR(255),
    PRIMARY KEY (rev, id),
    CONSTRAINT fk_drawdown_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE payment_aud (
    rev            INTEGER       NOT NULL,
    revtype        SMALLINT,
    id             BIGINT        NOT NULL,
    drawdown_id    BIGINT,
    bank           VARCHAR(255),
    transfer_mode  VARCHAR(255),
    amount         NUMERIC(15,2),
    status         VARCHAR(255),
    bank_reference VARCHAR(255),
    PRIMARY KEY (rev, id),
    CONSTRAINT fk_payment_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

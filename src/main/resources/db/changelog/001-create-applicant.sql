--liquibase formatted sql

--changeset starter:001-create-applicant
CREATE TABLE applicant (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    status      VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

--changeset starter:001-create-revinfo
CREATE TABLE revinfo (
    rev         SERIAL PRIMARY KEY,
    revtstmp    BIGINT
);

--changeset starter:001-create-applicant-aud
CREATE TABLE applicant_aud (
    id          BIGINT       NOT NULL,
    rev         INTEGER      NOT NULL REFERENCES revinfo(rev),
    revtype     SMALLINT,
    name        VARCHAR(255),
    email       VARCHAR(255),
    status      VARCHAR(50),
    PRIMARY KEY (id, rev)
);

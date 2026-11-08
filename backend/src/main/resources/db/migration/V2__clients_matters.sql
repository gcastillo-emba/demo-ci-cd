-- V2: clientes (Client) y asuntos (Matter) — cambio add-clients-matters (design D2).

CREATE TABLE clients (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    tax_id     VARCHAR(50)  NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

-- taxId único solo cuando está informado: varios clientes sin taxId pueden coexistir.
CREATE UNIQUE INDEX ux_clients_tax_id ON clients (tax_id) WHERE tax_id IS NOT NULL;
CREATE INDEX ix_clients_name_lower ON clients (lower(name));

CREATE TABLE matters (
    id          UUID          PRIMARY KEY,
    code        VARCHAR(12)   NOT NULL,
    client_id   UUID          NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    description VARCHAR(2000) NULL,
    status      VARCHAR(6)    NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    closed_at   TIMESTAMPTZ   NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_matters_code UNIQUE (code),
    CONSTRAINT fk_matters_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT ck_matters_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_matters_closed_at CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

CREATE INDEX ix_matters_client_status ON matters (client_id, status);
CREATE INDEX ix_matters_title_lower ON matters (lower(title));

-- Correlativo anual de MatterCode (AS-YYYY-NNNN), incrementado con un upsert atómico (design D3).
CREATE TABLE matter_code_seq (
    year       INT PRIMARY KEY,
    last_value INT NOT NULL
);

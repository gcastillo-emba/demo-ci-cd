-- V3: registros de horas (TimeEntry) — cambio add-time-entries (design D1, D4, D9).
-- Horas en décimas enteras (1..240 = 0,1..24 h); version = bloqueo optimista (JPA @Version).

CREATE TABLE time_entries (
    id               UUID          PRIMARY KEY,
    matter_id        UUID          NOT NULL,
    lawyer_id        UUID          NOT NULL,
    work_date        DATE          NOT NULL,
    hours_tenths     SMALLINT      NOT NULL,
    description      VARCHAR(2000) NOT NULL,
    status           VARCHAR(8)    NOT NULL,
    rejection_reason VARCHAR(500)  NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_time_entries_matter FOREIGN KEY (matter_id) REFERENCES matters (id),
    CONSTRAINT fk_time_entries_lawyer FOREIGN KEY (lawyer_id) REFERENCES users (id),
    CONSTRAINT ck_time_entries_hours CHECK (hours_tenths BETWEEN 1 AND 240),
    CONSTRAINT ck_time_entries_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_time_entries_rejection_reason
        CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT ck_time_entries_version CHECK (version >= 0)
);

-- Suma diaria por abogado (límite de 24 h) y filtro por abogado.
CREATE INDEX ix_time_entries_lawyer_work_date ON time_entries (lawyer_id, work_date);
-- Filtro por asunto.
CREATE INDEX ix_time_entries_matter ON time_entries (matter_id);
-- Cola de aprobaciones: status = PENDING ordenado por created_at.
CREATE INDEX ix_time_entries_status_created_at ON time_entries (status, created_at);

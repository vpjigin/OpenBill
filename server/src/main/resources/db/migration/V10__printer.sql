CREATE TABLE printer (
    id              TEXT    PRIMARY KEY,
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL,
    version         INTEGER NOT NULL DEFAULT 0,
    deleted_at      INTEGER,
    name            TEXT    NOT NULL,
    kind            TEXT    NOT NULL CHECK (kind IN ('SYSTEM', 'NETWORK')),
    system_name     TEXT,
    host            TEXT,
    port            INTEGER NOT NULL DEFAULT 9100,
    paper_mm        INTEGER NOT NULL DEFAULT 80 CHECK (paper_mm IN (58, 80)),
    chars_per_line  INTEGER,
    open_drawer     INTEGER NOT NULL DEFAULT 0,
    cut             INTEGER NOT NULL DEFAULT 1,
    is_default      INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_printer_default ON printer (is_default) WHERE deleted_at IS NULL;

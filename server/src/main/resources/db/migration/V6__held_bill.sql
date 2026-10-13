CREATE TABLE held_bill (
    id         TEXT    PRIMARY KEY,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    version    INTEGER NOT NULL DEFAULT 0,
    deleted_at INTEGER,
    label      TEXT,
    payload    TEXT    NOT NULL,
    created_by TEXT    NOT NULL REFERENCES app_user (id),
    channel    TEXT    NOT NULL
);
CREATE INDEX idx_held_bill_created ON held_bill (created_at);

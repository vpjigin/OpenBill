CREATE TABLE customer (
    id          TEXT    PRIMARY KEY,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL,
    version     INTEGER NOT NULL DEFAULT 0,
    deleted_at  INTEGER,
    name        TEXT    NOT NULL,
    phone       TEXT,
    gstin       TEXT,
    state_code  TEXT,
    address     TEXT,
    email       TEXT
);
CREATE INDEX idx_customer_phone ON customer (phone);
CREATE INDEX idx_customer_gstin ON customer (gstin);
CREATE INDEX idx_customer_name ON customer (name COLLATE NOCASE);

ALTER TABLE invoice ADD COLUMN customer_id TEXT REFERENCES customer (id);
ALTER TABLE invoice ADD COLUMN customer_state_code TEXT;
ALTER TABLE invoice ADD COLUMN customer_address TEXT;

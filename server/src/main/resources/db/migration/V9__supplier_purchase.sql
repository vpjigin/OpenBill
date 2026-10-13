ALTER TABLE item ADD COLUMN cost_paise INTEGER NOT NULL DEFAULT 0;

CREATE TABLE supplier (
    id          TEXT    PRIMARY KEY,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL,
    version     INTEGER NOT NULL DEFAULT 0,
    deleted_at  INTEGER,
    name        TEXT    NOT NULL,
    phone       TEXT,
    gstin       TEXT,
    state_code  TEXT,
    address     TEXT
);

CREATE TABLE purchase (
    id               TEXT    PRIMARY KEY,
    created_at       INTEGER NOT NULL,
    updated_at       INTEGER NOT NULL,
    version          INTEGER NOT NULL DEFAULT 0,
    deleted_at       INTEGER,
    supplier_id      TEXT    NOT NULL REFERENCES supplier (id),
    supplier_name    TEXT    NOT NULL,
    supplier_gstin   TEXT,
    bill_no          TEXT    NOT NULL,
    bill_date        INTEGER NOT NULL,
    inter_state      INTEGER NOT NULL,
    taxable_paise    INTEGER NOT NULL,
    cgst_paise       INTEGER NOT NULL,
    sgst_paise       INTEGER NOT NULL,
    igst_paise       INTEGER NOT NULL,
    round_off_paise  INTEGER NOT NULL,
    total_paise      INTEGER NOT NULL,
    notes            TEXT,
    created_by       TEXT    NOT NULL REFERENCES app_user (id)
);
CREATE UNIQUE INDEX idx_purchase_supplier_bill ON purchase (supplier_id, bill_no) WHERE deleted_at IS NULL;
CREATE INDEX idx_purchase_bill_date ON purchase (bill_date);

CREATE TABLE purchase_line (
    id             TEXT    PRIMARY KEY,
    created_at     INTEGER NOT NULL,
    updated_at     INTEGER NOT NULL,
    version        INTEGER NOT NULL DEFAULT 0,
    deleted_at     INTEGER,
    purchase_id    TEXT    NOT NULL REFERENCES purchase (id),
    line_no        INTEGER NOT NULL,
    item_id        TEXT    REFERENCES item (id),
    name           TEXT    NOT NULL,
    hsn            TEXT,
    unit           TEXT    NOT NULL,
    qty_milli      INTEGER NOT NULL CHECK (qty_milli > 0),
    cost_paise     INTEGER NOT NULL,
    gst_rate_bp    INTEGER NOT NULL,
    taxable_paise  INTEGER NOT NULL,
    cgst_paise     INTEGER NOT NULL,
    sgst_paise     INTEGER NOT NULL,
    igst_paise     INTEGER NOT NULL,
    total_paise    INTEGER NOT NULL
);
CREATE INDEX idx_purchase_line_purchase ON purchase_line (purchase_id);

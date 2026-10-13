-- Decimal quantities (milli-units), tax-inclusive item prices, gross/discount on invoices.

ALTER TABLE item ADD COLUMN stock_milli INTEGER NOT NULL DEFAULT 0;
ALTER TABLE item ADD COLUMN tax_inclusive INTEGER NOT NULL DEFAULT 0;
UPDATE item SET stock_milli = stock_qty * 1000;

ALTER TABLE invoice ADD COLUMN gross_paise INTEGER NOT NULL DEFAULT 0;
ALTER TABLE invoice ADD COLUMN discount_paise INTEGER NOT NULL DEFAULT 0;
UPDATE invoice SET gross_paise = taxable_paise;

CREATE TABLE invoice_line_new (
    id             TEXT    PRIMARY KEY,
    created_at     INTEGER NOT NULL,
    updated_at     INTEGER NOT NULL,
    version        INTEGER NOT NULL DEFAULT 0,
    deleted_at     INTEGER,
    invoice_id     TEXT    NOT NULL REFERENCES invoice (id),
    line_no        INTEGER NOT NULL,
    item_id        TEXT    REFERENCES item (id),
    name           TEXT    NOT NULL,
    hsn            TEXT,
    qty_milli      INTEGER NOT NULL CHECK (qty_milli > 0),
    unit           TEXT    NOT NULL DEFAULT 'NOS',
    tax_inclusive  INTEGER NOT NULL DEFAULT 0,
    rate_paise     INTEGER NOT NULL,
    gst_rate_bp    INTEGER NOT NULL,
    gross_paise    INTEGER NOT NULL DEFAULT 0,
    discount_paise INTEGER NOT NULL DEFAULT 0,
    taxable_paise  INTEGER NOT NULL,
    cgst_paise     INTEGER NOT NULL,
    sgst_paise     INTEGER NOT NULL,
    igst_paise     INTEGER NOT NULL,
    total_paise    INTEGER NOT NULL
);

INSERT INTO invoice_line_new (
    id, created_at, updated_at, version, deleted_at, invoice_id, line_no, item_id, name, hsn,
    qty_milli, unit, tax_inclusive, rate_paise, gst_rate_bp, gross_paise, discount_paise,
    taxable_paise, cgst_paise, sgst_paise, igst_paise, total_paise)
SELECT
    id, created_at, updated_at, version, deleted_at, invoice_id, line_no, item_id, name, hsn,
    qty * 1000, 'NOS', 0, rate_paise, gst_rate_bp, taxable_paise, 0,
    taxable_paise, cgst_paise, sgst_paise, igst_paise, total_paise
FROM invoice_line;

DROP TABLE invoice_line;
ALTER TABLE invoice_line_new RENAME TO invoice_line;
CREATE INDEX idx_invoice_line_invoice ON invoice_line (invoice_id);

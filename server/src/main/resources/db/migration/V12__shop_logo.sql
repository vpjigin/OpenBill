ALTER TABLE shop_settings ADD COLUMN logo TEXT;
ALTER TABLE shop_settings ADD COLUMN logo_mime TEXT;
ALTER TABLE shop_settings ADD COLUMN receipt_show_logo INTEGER NOT NULL DEFAULT 1;

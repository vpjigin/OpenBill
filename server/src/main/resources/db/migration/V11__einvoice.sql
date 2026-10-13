ALTER TABLE shop_settings ADD COLUMN pincode TEXT;
ALTER TABLE customer ADD COLUMN pincode TEXT;

ALTER TABLE invoice ADD COLUMN irn TEXT;
ALTER TABLE invoice ADD COLUMN ack_no TEXT;
ALTER TABLE invoice ADD COLUMN ack_date TEXT;
ALTER TABLE invoice ADD COLUMN signed_qr TEXT;

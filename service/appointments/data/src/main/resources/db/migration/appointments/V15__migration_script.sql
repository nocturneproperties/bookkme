ALTER TABLE appointment ADD COLUMN IF NOT EXISTS client_note_encrypted VARBINARY(8224) NULL;
CREATE TABLE IF NOT EXISTS saved_business (user_id BINARY(16) NOT NULL, business_id BINARY(16) NOT NULL, saved_at TIMESTAMP(6) NOT NULL, CONSTRAINT pk_saved_business PRIMARY KEY (user_id, business_id), CONSTRAINT fk_saved_business_business_id__id FOREIGN KEY (business_id) REFERENCES business_has_appointments(id) ON DELETE CASCADE ON UPDATE RESTRICT);
CREATE INDEX IF NOT EXISTS saved_business_business_id ON saved_business (business_id);

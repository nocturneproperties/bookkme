CREATE TABLE IF NOT EXISTS data_export (owner_id BINARY(16) NOT NULL PRIMARY KEY, state VARCHAR(16) NOT NULL, requested_at TIMESTAMP(6) NOT NULL, attempts INT NOT NULL DEFAULT 0, claim_id BINARY(16) NULL, claimed_until TIMESTAMP(6) NULL, finished_at TIMESTAMP(6) NULL, expires_at TIMESTAMP(6) NULL, document LONGBLOB NULL, key_id BINARY(16) NULL);
CREATE INDEX IF NOT EXISTS data_export_state ON data_export (state);
CREATE INDEX IF NOT EXISTS data_export_expires_at ON data_export (expires_at);

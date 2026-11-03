ALTER TABLE exhausted_event ADD COLUMN IF NOT EXISTS replay_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE business ADD COLUMN IF NOT EXISTS client_data_retention_months INT DEFAULT 36 NOT NULL;
ALTER TABLE client
    ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD INDEX IF NOT EXISTS client_business_id_last_activity_at (business_id, last_activity_at);
CREATE TABLE IF NOT EXISTS erased_client (client_id BINARY(16) NOT NULL PRIMARY KEY, business_id BINARY(16) NOT NULL, erased_at TIMESTAMP(6) NOT NULL, reapply_requested BOOLEAN NOT NULL DEFAULT FALSE);
CREATE TABLE IF NOT EXISTS data_export (owner_id BINARY(16) NOT NULL PRIMARY KEY, state VARCHAR(16) NOT NULL, requested_at TIMESTAMP(6) NOT NULL, attempts INT NOT NULL DEFAULT 0, claim_id BINARY(16) NULL, claimed_until TIMESTAMP(6) NULL, finished_at TIMESTAMP(6) NULL, expires_at TIMESTAMP(6) NULL, document LONGBLOB NULL);
CREATE INDEX IF NOT EXISTS data_export_state ON data_export (state);
CREATE INDEX IF NOT EXISTS data_export_expires_at ON data_export (expires_at);

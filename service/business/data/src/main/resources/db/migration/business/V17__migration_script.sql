ALTER TABLE exhausted_event ADD COLUMN IF NOT EXISTS replay_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE business ADD COLUMN IF NOT EXISTS client_data_retention_months INT DEFAULT 36 NOT NULL;
ALTER TABLE client
    ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD INDEX IF NOT EXISTS client_business_id_last_activity_at (business_id, last_activity_at);

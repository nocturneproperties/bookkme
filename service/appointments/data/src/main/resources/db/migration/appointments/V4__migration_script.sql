CREATE TABLE IF NOT EXISTS exhausted_event (id BINARY(16) PRIMARY KEY, createdAt TIMESTAMP(6) NOT NULL, updatedAt TIMESTAMP(6) NULL, original_topic VARCHAR(255) NOT NULL, idempotency_key VARCHAR(255) NOT NULL, attempt INT NOT NULL, payload BLOB NOT NULL);
CREATE INDEX exhausted_event_original_topic ON exhausted_event (original_topic);
ALTER TABLE exhausted_event ADD CONSTRAINT exhausted_event_idempotency_key_unique UNIQUE (idempotency_key);

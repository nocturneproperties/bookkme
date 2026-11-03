ALTER TABLE appointment
    RENAME COLUMN IF EXISTS user_id TO initiator_user_id,
    RENAME INDEX IF EXISTS appointment_user_id_business_id_date_start TO appointment_initiator_user_id_business_id_date_start,
    ADD COLUMN IF NOT EXISTS client_user_id BINARY(16) NULL,
    ADD INDEX IF NOT EXISTS appointment_client_user_id (client_user_id);
ALTER TABLE appointment_request
    RENAME COLUMN IF EXISTS user_id TO initiator_user_id,
    RENAME INDEX IF EXISTS appointment_request_user_id_business_id_date_start TO appointment_request_initiator_user_id_business_id_date_start,
    ADD COLUMN IF NOT EXISTS client_user_id BINARY(16) NULL,
    ADD INDEX IF NOT EXISTS appointment_request_client_user_id (client_user_id);
UPDATE appointment_request SET client_user_id = initiator_user_id;
CREATE TABLE IF NOT EXISTS deleted_user (user_id BINARY(16) NOT NULL PRIMARY KEY, deleted_at TIMESTAMP(6) NOT NULL);
CREATE TABLE IF NOT EXISTS deleted_client (client_id BINARY(16) NOT NULL PRIMARY KEY, business_id BINARY(16) NOT NULL, deleted_at TIMESTAMP(6) NOT NULL);
ALTER TABLE exhausted_event ADD COLUMN IF NOT EXISTS replay_requested BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS appointment_request_date_end ON appointment_request (date_end);

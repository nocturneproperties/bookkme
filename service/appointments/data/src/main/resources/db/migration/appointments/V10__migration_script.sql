ALTER TABLE appointment
    RENAME COLUMN user_id TO initiator_user_id,
    RENAME INDEX appointment_user_id_business_id_date_start TO appointment_initiator_user_id_business_id_date_start,
    ADD COLUMN client_user_id BINARY(16) NULL,
    ADD INDEX appointment_client_user_id (client_user_id);
ALTER TABLE appointment_request
    RENAME COLUMN user_id TO initiator_user_id,
    RENAME INDEX appointment_request_user_id_business_id_date_start TO appointment_request_initiator_user_id_business_id_date_start,
    ADD COLUMN client_user_id BINARY(16) NULL,
    ADD INDEX appointment_request_client_user_id (client_user_id);
UPDATE appointment SET client_user_id = initiator_user_id;
UPDATE appointment_request SET client_user_id = initiator_user_id;
CREATE TABLE IF NOT EXISTS deleted_user (user_id BINARY(16) NOT NULL PRIMARY KEY, deleted_at TIMESTAMP(6) NOT NULL);

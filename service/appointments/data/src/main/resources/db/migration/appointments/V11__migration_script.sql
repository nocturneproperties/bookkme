CREATE DATABASE IF NOT EXISTS `appointments_short_retention`;
CREATE TABLE IF NOT EXISTS `appointments_short_retention`.subject_key (subject_id BINARY(16) NOT NULL PRIMARY KEY, wrapped_key VARBINARY(60) NULL, master_key_version INT NULL, created_at TIMESTAMP(6) NOT NULL, shredded_at TIMESTAMP(6) NULL);
ALTER TABLE business_has_appointments ADD COLUMN IF NOT EXISTS owner_user_id BINARY(16) NULL, ADD INDEX IF NOT EXISTS business_has_appointments_owner_user_id (owner_user_id);

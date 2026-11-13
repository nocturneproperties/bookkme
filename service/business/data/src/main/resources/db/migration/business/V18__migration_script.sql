ALTER TABLE client
    DROP INDEX IF EXISTS client_business_id_phone_unique,
    DROP INDEX IF EXISTS client_phone,
    ADD COLUMN IF NOT EXISTS deletion_hold BOOLEAN NOT NULL DEFAULT FALSE;
CREATE DATABASE IF NOT EXISTS `business_short_retention`;
CREATE TABLE IF NOT EXISTS `business_short_retention`.subject_key (subject_id BINARY(16) NOT NULL PRIMARY KEY, wrapped_key VARBINARY(60) NULL, master_key_version INT NULL, created_at TIMESTAMP(6) NOT NULL, shredded_at TIMESTAMP(6) NULL);

ALTER TABLE client
    DROP INDEX IF EXISTS client_business_id_phone_unique,
    DROP INDEX IF EXISTS client_phone,
    ADD COLUMN IF NOT EXISTS deletion_hold BOOLEAN NOT NULL DEFAULT FALSE;
CREATE DATABASE IF NOT EXISTS `business_short_retention`;
CREATE TABLE IF NOT EXISTS `business_short_retention`.subject_key (subject_id BINARY(16) NOT NULL PRIMARY KEY, wrapped_key VARBINARY(60) NULL, master_key_version INT NULL, created_at TIMESTAMP(6) NOT NULL, shredded_at TIMESTAMP(6) NULL);
ALTER TABLE data_export ADD COLUMN IF NOT EXISTS key_id BINARY(16) NULL;
DELETE FROM data_export WHERE document IS NOT NULL AND key_id IS NULL;
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS personal_data_encrypted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE exhausted_event ADD COLUMN IF NOT EXISTS personal_data_encrypted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE client MODIFY COLUMN IF EXISTS `name` VARCHAR(512) NULL, MODIFY COLUMN IF EXISTS lastname VARCHAR(512) NULL, ADD COLUMN IF NOT EXISTS name_encrypted VARBINARY(2080) NULL, ADD COLUMN IF NOT EXISTS lastname_encrypted VARBINARY(2080) NULL, ADD COLUMN IF NOT EXISTS phone_encrypted VARBINARY(2080) NULL, ADD COLUMN IF NOT EXISTS email_encrypted VARBINARY(2080) NULL, ADD COLUMN IF NOT EXISTS description_encrypted VARBINARY(4128) NULL, ADD COLUMN IF NOT EXISTS email_index CHAR(44) NULL, ADD CONSTRAINT client_business_id_email_index_unique UNIQUE IF NOT EXISTS (business_id, email_index);
ALTER TABLE employee MODIFY COLUMN IF EXISTS `name` VARCHAR(512) NULL, MODIFY COLUMN IF EXISTS lastname VARCHAR(512) NULL, ADD COLUMN IF NOT EXISTS name_encrypted VARBINARY(2080) NULL, ADD COLUMN IF NOT EXISTS lastname_encrypted VARBINARY(2080) NULL, ADD COLUMN IF NOT EXISTS phone_encrypted VARBINARY(2080) NULL, ADD COLUMN IF NOT EXISTS email_encrypted VARBINARY(2080) NULL;
CREATE TABLE IF NOT EXISTS shredded_subject (subject_id BINARY(16) NOT NULL PRIMARY KEY, shredded_at TIMESTAMP(6) NOT NULL);
INSERT IGNORE INTO shredded_subject (subject_id, shredded_at) SELECT subject_id, shredded_at FROM `business_short_retention`.subject_key WHERE shredded_at IS NOT NULL;

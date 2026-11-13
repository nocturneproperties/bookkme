DROP TABLE IF EXISTS contact_form;
CREATE DATABASE IF NOT EXISTS `user_short_retention`;
CREATE TABLE IF NOT EXISTS `user_short_retention`.subject_key (subject_id BINARY(16) NOT NULL PRIMARY KEY, wrapped_key VARBINARY(60) NULL, master_key_version INT NULL, created_at TIMESTAMP(6) NOT NULL, shredded_at TIMESTAMP(6) NULL);
ALTER TABLE data_export ADD COLUMN IF NOT EXISTS key_id BINARY(16) NULL;
DELETE FROM data_export WHERE document IS NOT NULL AND key_id IS NULL;

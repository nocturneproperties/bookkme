UPDATE notification_channels SET available_to_clients = TRUE WHERE channel = 1;
CREATE DATABASE IF NOT EXISTS `notifications_short_retention`;
CREATE TABLE IF NOT EXISTS `notifications_short_retention`.subject_key (subject_id BINARY(16) NOT NULL PRIMARY KEY, wrapped_key VARBINARY(60) NULL, master_key_version INT NULL, created_at TIMESTAMP(6) NOT NULL, shredded_at TIMESTAMP(6) NULL);
ALTER TABLE exhausted_event ADD COLUMN IF NOT EXISTS personal_data_encrypted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notification_email_targets MODIFY COLUMN IF EXISTS email TEXT NULL, ADD COLUMN IF NOT EXISTS email_encrypted VARBINARY(1320) NULL;
ALTER TABLE notification_telegram_targets MODIFY COLUMN IF EXISTS telegram_tag TEXT NULL, ADD COLUMN IF NOT EXISTS telegram_tag_encrypted VARBINARY(1100) NULL;

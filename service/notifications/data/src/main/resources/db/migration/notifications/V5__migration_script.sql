UPDATE notification_channels SET available_to_clients = TRUE WHERE channel = 1;
CREATE DATABASE IF NOT EXISTS `notifications_short_retention`;
CREATE TABLE IF NOT EXISTS `notifications_short_retention`.subject_key (subject_id BINARY(16) NOT NULL PRIMARY KEY, wrapped_key VARBINARY(60) NULL, master_key_version INT NULL, created_at TIMESTAMP(6) NOT NULL, shredded_at TIMESTAMP(6) NULL);

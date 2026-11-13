CREATE DATABASE IF NOT EXISTS `appointments_short_retention`;
CREATE TABLE IF NOT EXISTS `appointments_short_retention`.subject_key (subject_id BINARY(16) NOT NULL PRIMARY KEY, wrapped_key VARBINARY(60) NOT NULL, master_key_version INT NOT NULL, created_at TIMESTAMP(6) NOT NULL);

DROP TABLE IF EXISTS contact_form;
CREATE DATABASE IF NOT EXISTS `user_keys`;
CREATE TABLE IF NOT EXISTS `user_keys`.subject_key (subject_id BINARY(16) NOT NULL PRIMARY KEY, wrapped_key VARBINARY(60) NOT NULL, master_key_version INT NOT NULL, created_at TIMESTAMP(6) NOT NULL);

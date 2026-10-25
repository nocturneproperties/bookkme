CREATE TABLE IF NOT EXISTS profile (id BINARY(16) PRIMARY KEY, `name` VARCHAR(255) NOT NULL, last_name VARCHAR(255) NOT NULL, email VARCHAR(320) NOT NULL, phone VARCHAR(512) NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL);
ALTER TABLE profile ADD CONSTRAINT profile_email_unique UNIQUE (email);
CREATE TABLE IF NOT EXISTS contact_form (id BINARY(16) PRIMARY KEY, user_id BINARY(16) NOT NULL, text text NOT NULL, usage_logs text NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, status TINYINT NOT NULL, CONSTRAINT fk_contact_form_user_id__id FOREIGN KEY (user_id) REFERENCES profile(id) ON DELETE CASCADE ON UPDATE RESTRICT);
CREATE INDEX contact_form_user_id ON contact_form (user_id);

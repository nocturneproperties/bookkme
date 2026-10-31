UPDATE client SET phone = NULL WHERE TRIM(phone) = '';
UPDATE client SET email = NULL WHERE TRIM(email) = '';
ALTER TABLE client ADD CONSTRAINT client_business_id_phone_unique UNIQUE (business_id, phone);
ALTER TABLE client ADD CONSTRAINT client_business_id_email_unique UNIQUE (business_id, email);

ALTER TABLE client ADD CONSTRAINT client_business_id_user_id_unique UNIQUE (business_id, user_id);

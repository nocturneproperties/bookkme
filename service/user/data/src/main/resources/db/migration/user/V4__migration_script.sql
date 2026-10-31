UPDATE profile SET phone = NULL WHERE TRIM(phone) = '';
UPDATE profile SET phone = TRIM(phone) WHERE phone IS NOT NULL;
ALTER TABLE profile ADD CONSTRAINT profile_phone_unique UNIQUE (phone);

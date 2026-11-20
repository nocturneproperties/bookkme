ALTER TABLE profile ADD COLUMN IF NOT EXISTS telegram_user_id_encrypted VARBINARY(128) NULL;

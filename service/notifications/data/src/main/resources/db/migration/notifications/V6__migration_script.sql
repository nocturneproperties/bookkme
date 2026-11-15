ALTER TABLE notification_email_targets DROP COLUMN IF EXISTS email, MODIFY COLUMN IF EXISTS email_encrypted VARBINARY(1320) NOT NULL;
ALTER TABLE notification_telegram_targets DROP COLUMN IF EXISTS telegram_tag, MODIFY COLUMN IF EXISTS telegram_tag_encrypted VARBINARY(1100) NOT NULL;

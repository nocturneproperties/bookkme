ALTER TABLE business_has_appointments MODIFY COLUMN IF EXISTS owner_user_id BINARY(16) NOT NULL;
ALTER TABLE appointment DROP COLUMN IF EXISTS employee_name, DROP COLUMN IF EXISTS client_name, DROP COLUMN IF EXISTS client_phone, DROP COLUMN IF EXISTS client_email, DROP COLUMN IF EXISTS note;
ALTER TABLE appointment_request DROP COLUMN IF EXISTS employee_name, DROP COLUMN IF EXISTS client_name, DROP COLUMN IF EXISTS client_phone, DROP COLUMN IF EXISTS client_email, DROP COLUMN IF EXISTS note;

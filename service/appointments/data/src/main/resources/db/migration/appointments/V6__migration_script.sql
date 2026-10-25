ALTER TABLE appointment_settings ADD automatic_completion BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE appointment ADD completed_by INT NULL;
UPDATE appointment SET completed_by = 0 WHERE status = 1;

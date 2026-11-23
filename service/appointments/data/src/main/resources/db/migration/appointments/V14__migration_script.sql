ALTER TABLE appointment_price_adjustment_services ADD COLUMN IF NOT EXISTS count INT DEFAULT 1 NOT NULL;
UPDATE appointment_price_adjustment_services s JOIN (SELECT MIN(id) AS keeper_id, COUNT(*) AS copies FROM appointment_price_adjustment_services GROUP BY price_adjustment_id, service_id HAVING COUNT(*) > 1) d ON s.id = d.keeper_id SET s.count = d.copies WHERE s.count = 1;
DELETE s FROM appointment_price_adjustment_services s JOIN (SELECT price_adjustment_id, service_id, MIN(id) AS keeper_id FROM appointment_price_adjustment_services GROUP BY price_adjustment_id, service_id HAVING COUNT(*) > 1) d ON s.price_adjustment_id = d.price_adjustment_id AND s.service_id = d.service_id AND s.id <> d.keeper_id;
ALTER TABLE appointment_price_adjustment_services ADD UNIQUE INDEX IF NOT EXISTS appointment_price_adjustment_services_adjustment_service_unique (price_adjustment_id, service_id);
ALTER TABLE appointment ADD COLUMN IF NOT EXISTS client_note_encrypted VARBINARY(8224) NULL;
CREATE TABLE IF NOT EXISTS saved_business (user_id BINARY(16) NOT NULL, business_id BINARY(16) NOT NULL, saved_at TIMESTAMP(6) NOT NULL, CONSTRAINT pk_saved_business PRIMARY KEY (user_id, business_id), CONSTRAINT fk_saved_business_business_id__id FOREIGN KEY (business_id) REFERENCES business_has_appointments(id) ON DELETE CASCADE ON UPDATE RESTRICT);
CREATE INDEX IF NOT EXISTS saved_business_business_id ON saved_business (business_id);
CREATE INDEX IF NOT EXISTS appointment_employee_id_date_end ON appointment (employee_id, date_end);

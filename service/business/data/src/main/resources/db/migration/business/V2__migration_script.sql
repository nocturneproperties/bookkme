CREATE TABLE IF NOT EXISTS employee_working_hours (id BINARY(16) PRIMARY KEY, createdAt TIMESTAMP(6) NOT NULL, updatedAt TIMESTAMP(6) NULL, employee_id BINARY(16) NOT NULL, day_of_week TINYINT NOT NULL, start_time TIME NOT NULL, end_time TIME NOT NULL, CONSTRAINT fk_employee_working_hours_employee_id__id FOREIGN KEY (employee_id) REFERENCES employee(id) ON DELETE CASCADE ON UPDATE RESTRICT);
CREATE INDEX employee_working_hours_employee_id ON employee_working_hours (employee_id);

CREATE TABLE IF NOT EXISTS employee_day_offs (id BINARY(16) PRIMARY KEY, createdAt TIMESTAMP(6) NOT NULL, updatedAt TIMESTAMP(6) NULL, employee_id BINARY(16) NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL, CONSTRAINT fk_employee_day_offs_employee_id__id FOREIGN KEY (employee_id) REFERENCES employee(id) ON DELETE CASCADE ON UPDATE RESTRICT);
CREATE INDEX employee_day_offs_employee_id ON employee_day_offs (employee_id);
CREATE INDEX employee_day_offs_employee_id_start_date ON employee_day_offs (employee_id, start_date);

ALTER TABLE employee ADD working_days TINYINT DEFAULT 0 NOT NULL;

ALTER TABLE employee_invitation
    MODIFY COLUMN email VARCHAR(512) NOT NULL,
    DROP INDEX employee_invitation_business_id_user_id_unique,
    DROP INDEX employee_invitation_user_id,
    DROP COLUMN user_id,
    DROP COLUMN `name`,
    DROP COLUMN lastname,
    DROP COLUMN phone,
    ADD CONSTRAINT employee_invitation_business_id_email_unique UNIQUE (business_id, email);
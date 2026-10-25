ALTER TABLE employee_invitation
    DROP INDEX employee_invitation_business_id_email_unique,
    ADD code VARCHAR(16) NOT NULL,
    ADD CONSTRAINT employee_invitation_code_unique UNIQUE (code),
    DROP COLUMN email;

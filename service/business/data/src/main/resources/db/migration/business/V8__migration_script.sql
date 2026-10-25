ALTER TABLE employee_invitation
    CHANGE COLUMN code code_hash VARCHAR(64) NULL,
    DROP INDEX employee_invitation_code_unique,
    ADD CONSTRAINT employee_invitation_code_hash_unique UNIQUE (code_hash);

UPDATE employee_invitation SET code_hash = SHA2(code_hash, 256) WHERE code_hash IS NOT NULL;

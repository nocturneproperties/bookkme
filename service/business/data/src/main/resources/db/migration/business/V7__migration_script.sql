ALTER TABLE employee_invitation
    MODIFY COLUMN code VARCHAR(16) NULL;

UPDATE employee_invitation SET code = NULL WHERE status <> 0;

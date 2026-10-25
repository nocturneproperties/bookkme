ALTER TABLE business_permission_grants ADD employee_id BINARY(16) NULL;
UPDATE business_permission_grants g JOIN employee e ON e.user_id = g.user_id AND e.business_id = g.business_id SET g.employee_id = e.id;
DELETE FROM business_permission_grants WHERE employee_id IS NULL;
ALTER TABLE business_permission_grants MODIFY employee_id BINARY(16) NOT NULL;
CREATE INDEX business_permission_grants_employee_id ON business_permission_grants (employee_id);
ALTER TABLE business_permission_grants ADD CONSTRAINT fk_business_permission_grants_employee_id__id FOREIGN KEY (employee_id) REFERENCES employee(id) ON DELETE CASCADE ON UPDATE RESTRICT;

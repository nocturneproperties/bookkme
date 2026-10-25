ALTER TABLE employee ADD CONSTRAINT employee_business_id_user_id_unique UNIQUE (business_id, user_id);

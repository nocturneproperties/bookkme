CREATE INDEX IF NOT EXISTS appointment_employee_user_id ON appointment (employee_user_id);
CREATE INDEX IF NOT EXISTS appointment_request_employee_user_id ON appointment_request (employee_user_id);

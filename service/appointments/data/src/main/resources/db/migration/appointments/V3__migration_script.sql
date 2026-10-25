CREATE TABLE IF NOT EXISTS appointment_permission_grants (id BINARY(16) PRIMARY KEY, createdAt TIMESTAMP(6) NOT NULL, updatedAt TIMESTAMP(6) NULL, userId BINARY(16) NOT NULL, business_id BINARY(16) NOT NULL, can_view BOOLEAN NOT NULL, can_update BOOLEAN NOT NULL, can_delete BOOLEAN NOT NULL, CONSTRAINT fk_appointment_permission_grants_business_id__id FOREIGN KEY (business_id) REFERENCES business_has_appointments(id) ON DELETE CASCADE ON UPDATE RESTRICT);
ALTER TABLE appointment_permission_grants ADD CONSTRAINT appointment_permission_grants_userId_business_id_unique UNIQUE (userId, business_id);

-- Backfill from the legacy single-level user_has_appointment_permissions grant.
-- OWNER(100) -> full control, EDIT(2) -> view+update, READ(1) -> view-only
INSERT INTO appointment_permission_grants (id, createdAt, updatedAt, userId, business_id, can_view, can_update, can_delete)
SELECT id, createdAt, updatedAt, userId, business_id, TRUE, TRUE, TRUE
FROM user_has_appointment_permissions WHERE permission = 100;

INSERT INTO appointment_permission_grants (id, createdAt, updatedAt, userId, business_id, can_view, can_update, can_delete)
SELECT id, createdAt, updatedAt, userId, business_id, TRUE, TRUE, FALSE
FROM user_has_appointment_permissions WHERE permission = 2;

INSERT INTO appointment_permission_grants (id, createdAt, updatedAt, userId, business_id, can_view, can_update, can_delete)
SELECT id, createdAt, updatedAt, userId, business_id, TRUE, FALSE, FALSE
FROM user_has_appointment_permissions WHERE permission = 1;

DROP TABLE user_has_appointment_permissions;

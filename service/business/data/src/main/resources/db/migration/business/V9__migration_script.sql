CREATE TABLE IF NOT EXISTS business_permission_grants (id BINARY(16) PRIMARY KEY, user_id BINARY(16) NOT NULL, business_id BINARY(16) NOT NULL, resource INT NOT NULL, can_view BOOLEAN NOT NULL, can_update BOOLEAN NOT NULL, can_delete BOOLEAN NOT NULL, CONSTRAINT fk_business_permission_grants_business_id__id FOREIGN KEY (business_id) REFERENCES business(id) ON DELETE CASCADE ON UPDATE RESTRICT);
ALTER TABLE business_permission_grants ADD CONSTRAINT business_permission_grants_user_id_business_id_resource_unique UNIQUE (user_id, business_id, resource);

-- Backfill from the legacy single-level business_permissions grant.
-- Resource ordinals (BusinessResource): 0=BUSINESS, 1=EMPLOYEES, 2=CLIENTS, 3=SERVICES, 4=APPOINTMENTS
-- OWNER(100) -> full control of every resource
INSERT INTO business_permission_grants (id, user_id, business_id, resource, can_view, can_update, can_delete)
SELECT RANDOM_BYTES(16), user_id, business_id, resource, TRUE, TRUE, TRUE
FROM business_permissions
CROSS JOIN (SELECT 0 AS resource UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) resources
WHERE permission = 100;

-- EDIT(2) -> view+update on business and appointments, full control of clients and services (matches the old EDIT gate, which already allowed delete there), nothing on employees
INSERT INTO business_permission_grants (id, user_id, business_id, resource, can_view, can_update, can_delete)
SELECT RANDOM_BYTES(16), user_id, business_id, 0, TRUE, TRUE, FALSE
FROM business_permissions WHERE permission = 2;

INSERT INTO business_permission_grants (id, user_id, business_id, resource, can_view, can_update, can_delete)
SELECT RANDOM_BYTES(16), user_id, business_id, resource, TRUE, TRUE, TRUE
FROM business_permissions
CROSS JOIN (SELECT 2 AS resource UNION ALL SELECT 3) resources
WHERE permission = 2;

INSERT INTO business_permission_grants (id, user_id, business_id, resource, can_view, can_update, can_delete)
SELECT RANDOM_BYTES(16), user_id, business_id, 4, TRUE, TRUE, FALSE
FROM business_permissions WHERE permission = 2;

-- READ(1) -> view-only on clients and appointments
INSERT INTO business_permission_grants (id, user_id, business_id, resource, can_view, can_update, can_delete)
SELECT RANDOM_BYTES(16), user_id, business_id, resource, TRUE, FALSE, FALSE
FROM business_permissions
CROSS JOIN (SELECT 2 AS resource UNION ALL SELECT 4) resources
WHERE permission = 1;

DROP TABLE business_permissions;

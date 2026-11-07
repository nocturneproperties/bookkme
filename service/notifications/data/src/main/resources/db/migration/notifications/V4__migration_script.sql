ALTER TABLE device
    DROP INDEX IF EXISTS device_device_uuid_unique,
    DROP INDEX IF EXISTS device_auth_id_device_uuid_user_id_unique,
    ADD UNIQUE INDEX IF NOT EXISTS device_auth_id_device_uuid_unique (auth_id, device_uuid);

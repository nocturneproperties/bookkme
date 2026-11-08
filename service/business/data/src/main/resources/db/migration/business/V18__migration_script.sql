ALTER TABLE client
    DROP INDEX IF EXISTS client_business_id_phone_unique,
    DROP INDEX IF EXISTS client_phone;

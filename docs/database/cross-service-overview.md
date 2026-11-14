[← Database ER diagrams](README.md)

# Cross-service overview

No database enforces these edges — each service has its own schema/connection
pool, and the referencing column is a plain `uuid`. Consistency is maintained
via domain events (see `service/<svc>/client`) rather than DB constraints.

```mermaid
erDiagram
    PROFILE ||--o| AUTHENTICATION : "user_id"
    PROFILE ||--o| BUSINESS : "user_id (owner)"
    PROFILE ||--o{ EMPLOYEE : "user_id"
    PROFILE ||--o{ CLIENT : "user_id (nullable)"
    PROFILE ||--o{ BUSINESS_PERMISSION_GRANTS : "user_id"
    PROFILE ||--o{ APPOINTMENT_PERMISSION_GRANTS : "userId"
    PROFILE ||--o{ NOTIFICATION_SETTINGS : "user_id"
    PROFILE ||--o{ DEVICE : "user_id"
    AUTHENTICATION ||--o{ DEVICE : "auth_id"
    BUSINESS ||--|| BUSINESS_HAS_APPOINTMENTS : "id (replicated via events)"
    PROFILE ||--o{ BUSINESS_HAS_APPOINTMENTS : "owner_user_id (business.user_id, set on enable, kept by BusinessEvent.Updated, filled by appointments-fillBusinessOwners until 0.19.0, erased by AuthEvent.UserDeleted)"
    EMPLOYEE ||--o{ APPOINTMENT : "employee_id"
    PROFILE ||--o{ APPOINTMENT : "employee_user_id"
    PROFILE ||--o{ APPOINTMENT : "initiator_user_id (who booked)"
    PROFILE ||--o{ APPOINTMENT : "client_user_id (nullable, the client's account, filled by BusinessEvent.ClientLinked, erased by AuthEvent.UserDeleted)"
    PROFILE ||--o| DELETED_USER : "user_id of a deleted account"
    PROFILE ||--o| ERASED_ACCOUNT : "user_id of an erased account, re-erased after a backup restore"
    CLIENT ||--o{ APPOINTMENT : "client_id, duplicates moved by BusinessEvent.ClientLinked, erased by BusinessEvent.ClientDeleted"
    CLIENT ||--o| DELETED_CLIENT : "client_id of a deleted client"
    BUSINESS ||--o{ DELETED_CLIENT : "business_id"
    SERVICE ||--o{ APPOINTMENT_SERVICES : "service_id"
    SERVICE_GROUP ||--o{ APPOINTMENT_SERVICES : "service_group_id"
    SERVICE ||--o{ APPOINTMENT_PRICE_ADJUSTMENT_SERVICES : "service_id"
    SERVICE_GROUP ||--o{ APPOINTMENT_PRICE_ADJUSTMENT_SERVICES : "service_group_id"

    PROFILE {
        uuid id PK
    }
    AUTHENTICATION {
        uuid id PK
    }
    DEVICE {
        uuid id PK
        uuid auth_id UK "composite with device_uuid"
        uuid device_uuid UK "composite with auth_id"
    }
    BUSINESS {
        uuid id PK
    }
    BUSINESS_HAS_APPOINTMENTS {
        uuid id PK
    }
    EMPLOYEE {
        uuid id PK
    }
    CLIENT {
        uuid id PK
    }
    SERVICE {
        uuid id PK
    }
    SERVICE_GROUP {
        uuid id PK
    }
    APPOINTMENT_PRICE_ADJUSTMENT_SERVICES {
        uuid id PK
    }
    BUSINESS_PERMISSION_GRANTS {
        uuid id PK
    }
    APPOINTMENT_PERMISSION_GRANTS {
        uuid id PK
    }
    NOTIFICATION_SETTINGS {
        uuid id PK
    }
    APPOINTMENT {
        uuid id PK
    }
    APPOINTMENT_SERVICES {
        uuid id PK
    }
    DELETED_USER {
        uuid user_id PK
    }
    DELETED_CLIENT {
        uuid client_id PK
    }
    ERASED_ACCOUNT {
        uuid user_id PK
    }
```

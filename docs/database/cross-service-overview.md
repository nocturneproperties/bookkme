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
    EMPLOYEE ||--o{ APPOINTMENT : "employee_id"
    PROFILE ||--o{ APPOINTMENT : "employee_user_id"
    CLIENT ||--o{ APPOINTMENT : "client_id"
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
```

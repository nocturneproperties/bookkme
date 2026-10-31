[← Database ER diagrams](README.md)

# Business service

Largest schema — a business, its staff, services and clients.

```mermaid
erDiagram
    BUSINESS ||--o{ BUSINESS_DASHBOARD : "has"
    BUSINESS ||--o{ BUSINESS_DAY_OFFS : "has"
    BUSINESS ||--o{ BUSINESS_PERMISSION_GRANTS : "grants"
    BUSINESS ||--o{ BUSINESS_WORKING_HOURS : "has"
    BUSINESS ||--o{ CLIENT : "has"
    BUSINESS ||--o{ EMPLOYEE : "employs"
    BUSINESS ||--o{ EMPLOYEE_INVITATION : "sends"
    BUSINESS ||--o{ SERVICE_GROUP : "defines"
    BUSINESS ||--o{ SERVICE : "offers"
    BUSINESS ||--o{ EMPLOYEE_CAN_PROVIDE_SERVICE : "scopes"
    EMPLOYEE ||--o{ EMPLOYEE_CAN_PROVIDE_SERVICE : "can provide"
    SERVICE ||--o{ EMPLOYEE_CAN_PROVIDE_SERVICE : "provided via"
    EMPLOYEE ||--o{ EMPLOYEE_DAY_OFFS : "has"
    EMPLOYEE ||--o{ BUSINESS_PERMISSION_GRANTS : "holds (cascade delete)"
    EMPLOYEE ||--o{ EMPLOYEE_WORKING_HOURS : "has"
    SERVICE_GROUP ||--o{ SERVICE : "groups"

    BUSINESS {
        uuid id PK
        uuid user_id UK "logical FK -> user.profile.id (owner)"
        string name
        string description
        string address
        double latitude
        double longitude
        string currency
        string timezone
        byte working_days
        string instagram
        string telegram
        string viber
        string whatsapp
        string phone
        timestamp created_at
        timestamp updated_at
    }

    BUSINESS_DASHBOARD {
        uuid id PK
        uuid user_id UK "logical FK -> user.profile.id"
        uuid business_id FK
        timestamp created_at
        timestamp updated_at
    }

    BUSINESS_DAY_OFFS {
        uuid id PK
        uuid business_id FK
        date start_date
        date end_date
        timestamp created_at
        timestamp updated_at
    }

    BUSINESS_PERMISSION_GRANTS {
        uuid id PK
        uuid user_id "logical FK -> user.profile.id; UK with business_id, resource"
        uuid business_id FK
        uuid employee_id FK "indexed; same employee as (user_id, business_id)"
        enum resource "BUSINESS | EMPLOYEES | CLIENTS | SERVICES | APPOINTMENTS; UK with user_id, business_id"
        bool can_view
        bool can_update
        bool can_delete
    }

    BUSINESS_WORKING_HOURS {
        uuid id PK
        uuid business_id FK
        byte day_of_week
        time start_time
        time end_time
        timestamp created_at
        timestamp updated_at
    }

    CLIENT {
        uuid id PK
        uuid business_id FK
        string name
        string lastname
        string phone "nullable; UK with business_id"
        string email "nullable; UK with business_id; at least one of phone/email required on create"
        uuid user_id "logical FK -> user.profile.id, nullable; UK with business_id"
        string description "nullable; business owner's notes about the client"
        timestamp source_updated_at
        timestamp created_at
        timestamp updated_at
    }

    EMPLOYEE {
        uuid id PK
        uuid business_id FK
        string name
        string lastname
        string phone
        string email
        uuid user_id "logical FK -> user.profile.id; UK with business_id"
        timestamp source_updated_at
        byte working_days
        timestamp suspended_at "nullable; set = suspended, grants read as NONE"
        timestamp created_at
        timestamp updated_at
    }

    EMPLOYEE_CAN_PROVIDE_SERVICE {
        uuid id PK
        uuid employee_id FK "UK with service_id"
        uuid service_id FK
        uuid business_id FK
    }

    EMPLOYEE_DAY_OFFS {
        uuid id PK
        uuid employee_id FK
        date start_date
        date end_date
        timestamp created_at
        timestamp updated_at
    }

    EMPLOYEE_INVITATION {
        uuid id PK
        uuid business_id FK
        uuid invited_by "logical FK -> user.profile.id"
        string code_hash UK "SHA-256 hex of the invite code, never the plaintext; nullable, cleared once the invitation leaves PENDING so the code can be reused"
        enum status
        timestamp created_at "indexed together with business_id for the daily invitation quota"
        timestamp updated_at "processed rows are deleted 30 days after this"
    }

    EMPLOYEE_WORKING_HOURS {
        uuid id PK
        uuid employee_id FK
        byte day_of_week
        time start_time
        time end_time
        timestamp created_at
        timestamp updated_at
    }

    SERVICE_GROUP {
        uuid id PK
        uuid business_id FK "UK with name"
        string name
        timestamp created_at
        timestamp updated_at
    }

    SERVICE {
        uuid id PK
        uuid business_id FK "UK with group_id, name"
        uuid group_id FK
        string name
        int duration
        string price_currency
        long price_unscaled
        int price_scale
        bool available
        timestamp created_at
        timestamp updated_at
    }

    EXHAUSTED_EVENT {
        uuid id PK
        string original_topic
        string idempotency_key UK
        int attempt
        blob payload
        timestamp createdAt
        timestamp updatedAt
    }

    OUTBOX_EVENT {
        long id PK "auto-increment; relay publishes in id order"
        string topic
        string partition_key "nullable"
        blob payload "ProtoBuf-encoded event, idempotency key included"
        timestamp createdAt
    }
```

`EXHAUSTED_EVENT` is not part of the business domain model — it's the shared DLT landing table (`core/data/eventstreaming/data`, one instance per service schema) that `KafkaEventConsumer`/`EmbeddedEventConsumer` write to when an event exhausts its retry budget, so a failed event survives a process restart or crash instead of being lost. See `AGENTS.md`'s event-streaming retry design notes.

`OUTBOX_EVENT` is not part of the business domain model either — it's the shared transactional outbox (`core/data/eventstreaming/data`, one instance per service schema). `eventProducer.send` inserts the encoded event in the same transaction as the business write, so an event exists exactly when its write committed; `OutboxRelay` then locks the oldest rows (`SELECT … FOR UPDATE`), publishes them to Kafka (or the embedded transport in the monolith) and deletes them. Delivery is at-least-once, deduplicated by consumers through the event's idempotency key. See [Transactional outbox](../transactional-outbox.md).

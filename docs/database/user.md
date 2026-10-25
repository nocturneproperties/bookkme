[← Database ER diagrams](README.md)

# User service

Smallest schema — the canonical user profile and inbound contact-form submissions.

```mermaid
erDiagram
    PROFILE ||--o{ CONTACT_FORM : "submits"

    PROFILE {
        uuid id PK
        string name
        string last_name
        string email UK
        string phone
        timestamp created_at
        timestamp updated_at
    }

    CONTACT_FORM {
        uuid id PK
        uuid user_id FK
        text text
        text usage_logs
        byte status
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
```

`EXHAUSTED_EVENT` is not part of the user domain model — it's the shared DLT landing table (`core/data/eventstreaming/data`, one instance per service schema) that `KafkaEventConsumer`/`EmbeddedEventConsumer` write to when an event exhausts its retry budget, so a failed event survives a process restart or crash instead of being lost. See `AGENTS.md`'s event-streaming retry design notes.

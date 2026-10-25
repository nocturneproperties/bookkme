[← Database ER diagrams](README.md)

# Notifications service

Fan-in tables keyed by `user_id`, feeding channel dispatch for a given user across devices/targets.

```mermaid
erDiagram
    NOTIFICATION_SETTINGS ||--o{ NOTIFICATION_CHANNELS : "has"

    DEVICE {
        uuid id PK
        uuid auth_id "logical FK -> authentication.id; UK with device_uuid, user_id"
        uuid device_uuid UK
        uuid user_id "logical FK -> user.profile.id"
        text notification_token
        enum language
        timestamp createdAt
        timestamp updatedAt
    }

    NOTIFICATION_SETTINGS {
        uuid id PK
        uuid user_id UK "logical FK -> user.profile.id"
        bool appointment_enabled
        timestamp createdAt
        timestamp updatedAt
    }

    NOTIFICATION_CHANNELS {
        uuid id PK
        uuid settings_id FK "UK with channel"
        enum channel
        bool enabled
        bool available_to_clients
        timestamp createdAt
        timestamp updatedAt
    }

    NOTIFICATION_EMAIL_TARGETS {
        uuid id PK
        uuid user_id UK "logical FK -> user.profile.id"
        text email
        timestamp source_updated_at
        timestamp createdAt
        timestamp updatedAt
    }

    NOTIFICATION_TELEGRAM_TARGETS {
        uuid id PK
        uuid user_id UK "logical FK -> user.profile.id"
        text telegram_tag
        timestamp createdAt
        timestamp updatedAt
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

`notification_email_targets` and `notification_telegram_targets` are not
foreign-keyed to `notification_settings` in the schema — they are joined to a
user's settings at query time via the shared `user_id`.

`EXHAUSTED_EVENT` is not part of the notifications domain model — it's the shared DLT landing table (`core/data/eventstreaming/data`, one instance per service schema) that `KafkaEventConsumer`/`EmbeddedEventConsumer` write to when an event exhausts its retry budget, so a failed event survives a process restart or crash instead of being lost. See `AGENTS.md`'s event-streaming retry design notes.

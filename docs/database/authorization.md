[← Database ER diagrams](README.md)

# Authorization service

Owns login/session/device and WebAuthn passkey state, keyed off `authentication.user_id`
(a logical reference to `user.profile.id`).

```mermaid
erDiagram
    AUTHENTICATION ||--o{ AUTH_DEVICE : "has"
    AUTHENTICATION ||--o{ PASSKEY_CREDENTIALS : "has"
    AUTHENTICATION ||--o| TELEGRAM_IDENTITY : "signs in with"
    AUTHENTICATION ||--o| ACCOUNT_DELETION : "scheduled for"

    AUTHENTICATION {
        uuid id PK
        uuid user_id UK "logical FK -> user.profile.id"
        uuid uuid UK
        timestamp created_at
        timestamp updated_at
        timestamp last_active_at "indexed; set on every token refresh and sign-in, at most once a day"
        int dormancy_stage "ACTIVE, SENT_60_DAY_WARNING, SENT_30_DAY_WARNING, SENT_7_DAY_WARNING (days before deletion); reset to ACTIVE by activity"
        timestamp dormancy_warned_at "nullable; when the first dormancy warning went out"
        boolean deletion_hold "default false; set by staff for a restriction or legal hold, auth-handleDormantAccounts and auth-handleScheduledDeletions skip the account"
        timestamp demo_expires_at "nullable, indexed; set only on a demo account (POST /api/auth/demo, 30 days), cleared by the upgrade; refresh is refused after it and auth-deleteExpiredDemoAccounts erases the account 7 days later"
    }

    AUTH_DEVICE {
        uuid id PK
        uuid user_auth_id FK
        uuid device_uuid UK "composite with user_auth_id"
        string device_name
        enum language
        uuid refresh_token_id
        string refresh_token_hash
        timestamp refresh_token_expires_at "7 days after the last refresh; for a demo account 7 days after demo_expires_at"
        uuid previous_refresh_token_id
        string previous_refresh_token_hash
        bool is_signed_in
        timestamp last_log_in_at
        timestamp created_at
        timestamp updated_at
    }

    PASSKEY_CREDENTIALS {
        uuid id PK
        uuid auth_id FK
        string name
        binary cred_descriptor_id UK "composite with auth_id"
        string cred_descriptor_type
        string cred_descriptor_transports
        text public_key
        long signature_count
        bool discoverable
        bool backup_eligible
        bool backed_up
        binary attestation_object
        string client_data
        timestamp created_at
        timestamp updated_at
        timestamp last_used_at
    }

    TELEGRAM_IDENTITY {
        uuid id PK
        uuid auth_id FK,UK "ON DELETE CASCADE"
        char telegram_id_index UK "HMAC blind index of the Telegram user id (APPLICATION_BLIND_INDEX_KEY); the id itself is not stored"
        char username_index "nullable; blind index of the last Telegram username the user service accepted, of the empty string for none; null until first synced"
        timestamp created_at
    }

    ACCOUNT_DELETION {
        uuid auth_id PK,FK "ON DELETE CASCADE; one pending deletion the user asked for"
        timestamp requested_at
        timestamp deletes_at "indexed; requested_at + 7 days, when auth-handleScheduledDeletions erases the account"
        string reminder "NONE, TWO_DAYS, ONE_DAY (stored by name); the last reminder sent"
    }

    EXHAUSTED_EVENT {
        uuid id PK
        string original_topic
        string idempotency_key UK
        int attempt
        blob payload
        bool replay_requested
        bool personal_data_encrypted "payload sealed under a person's key"
        timestamp createdAt
        timestamp updatedAt
    }

    OUTBOX_EVENT {
        long id PK "auto-increment; relay publishes in id order"
        string topic
        string partition_key "nullable"
        blob payload "ProtoBuf-encoded event, idempotency key included"
        timestamp createdAt
        bool personal_data_encrypted "payload sealed under a person's key"
    }

    ERASED_ACCOUNT {
        uuid user_id PK "logical FK -> user.profile.id of an erased account"
        timestamp erased_at "rows older than 60 days are deleted by auth-deleteExpiredErasureRecords"
        bool reapply_requested "set only by a backup restore, cleared by auth-reapplyErasures"
    }
```

`TELEGRAM_IDENTITY` links an account created or used through Telegram Mini App sign-in (`POST /api/auth/telegram/sign_in`) to its Telegram user, found by the blind index of the Telegram user id so the schema holds no plaintext identifier. It goes with the account (`ON DELETE CASCADE`), so an erased Telegram user who signs in again gets a new account. `username_index` lets sign-in skip the call to the user service while the Telegram username hasn't changed. Hidden from NocoDB, like the rest of `authorization`.

`ACCOUNT_DELETION` holds an account deletion the user asked for (`DELETE /api/auth/account`), due 7 days later; `DELETE /api/auth/account/deletion` removes it, and `auth-handleScheduledDeletions` sends the 2-day and 1-day reminders and erases the account when `deletes_at` has passed, which deletes the row with the account (`ON DELETE CASCADE`). A restore to a point before the request loses it. Hidden from NocoDB, like the rest of `authorization`.

`EXHAUSTED_EVENT` is not part of the authorization domain model — it's the shared DLT landing table (`library/outbox`, one instance per service schema) that `KafkaEventConsumer`/`EmbeddedEventConsumer` write to when an event exhausts its retry budget, so a failed event survives a process restart or crash instead of being lost. Every start, and every row an operator sets `replay_requested` on, is replayed through the consumer group's DLT topic by `ExhaustedEventReplayer`, which also exports the parked rows as the `events_exhausted` metric. See [Exhausted events](../exhausted-events.md).

`OUTBOX_EVENT` is not part of the authorization domain model either — it's the shared transactional outbox (`library/outbox`, one instance per service schema). `eventProducer.send` inserts the encoded event in the same transaction as the authorization write, so an event exists exactly when its write committed; `OutboxRelay` then locks the oldest rows (`SELECT … FOR UPDATE`), publishes them to Kafka (or the embedded transport in the monolith) and deletes them. Delivery is at-least-once, deduplicated by consumers through the event's idempotency key. See [Transactional outbox](../transactional-outbox.md).

`ERASED_ACCOUNT` records every account erasure (`AccountEraser`: requested account deletion when it is due, and dormant-account deletion), in the same transaction as the erasure, so a record exists exactly when its erasure committed. It holds the user id and the time only and is inserted once (`INSERT IGNORE`). A restore to an earlier point undoes the erasures made after it, so the restore tool reads these records from the latest backup and re-inserts them with `reapply_requested`; a record the restored database already holds is skipped. `auth-reapplyErasures` then erases those accounts again at the app's next start (see [Restoring prod](../../deployment/ansible/README.md#restoring-prod) and [Scheduled jobs](../operations/scheduled-jobs.md)). Rows live 60 days, twice the 30 days of backups. Hidden from NocoDB.

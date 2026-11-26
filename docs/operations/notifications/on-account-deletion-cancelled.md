# React to a cancelled account deletion

Kafka topic `AuthEvent.AccountDeletionCancelled` → `NotificationEventHandler` → `SendNotification`

Produced by [Cancel account deletion](../authorization/cancel-account-deletion.md)
when a pending deletion is removed. Tells the user their account won't be
deleted, and that they can ask again in the app if they didn't cancel it
themselves. The notification type is `ACCOUNT`, delivered like the
[scheduled deletion notice](on-account-deletion-scheduled.md): every enabled
channel plus email, and Telegram for an account with no real address.

```mermaid
flowchart TD
    Start([Consume AuthEvent.AccountDeletionCancelled]) --> Dedup{idempotencyKey already processed by notifications?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Settings[NotificationSettingsDataSource.getByUserId userId - in a transaction]
    Settings -- none --> Fail([Fails - retried through the notifications DLT topic])
    Settings -- found --> Deliverable{"NotificationTargetDataSource.getEmail userId - same transaction; a real address, not a placeholder?"}
    Deliverable -- Yes --> Channels["Send account.deletion_cancelled through every enabled channel plus EMAIL even when it is off - type ACCOUNT"]
    Deliverable -- No, none or placeholder --> ChannelsTelegram["Send account.deletion_cancelled through every enabled channel plus EMAIL and TELEGRAM even when they are off"]
    Channels --> Ack([Marked processed])
    ChannelsTelegram --> Ack
```

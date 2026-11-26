# React to a scheduled account deletion

Kafka topic `AuthEvent.AccountDeletionScheduled` → `NotificationEventHandler` → `SendNotification`

Produced by [Delete account](../authorization/delete-account.md) when the user
asks to delete their account (`reminder = false`), and by the
[`auth-handleScheduledDeletions` job](../scheduled-jobs.md) 2 days and 1 day
before the deletion (`reminder = true`, rendered as a reminder). Tells the user
when the account will be deleted (`deletesAt`, written in UTC since the user's
time zone isn't known) and that they can cancel in the app. Signing in does
not cancel it, unlike a dormancy warning.

The notification type is `ACCOUNT`, which the user can't switch off: it goes
through the channels the user has enabled and always by email, even with the
email channel off. An account with no real email address (an account created
through Telegram sign-in, until it sets a real address) gets it on Telegram as
well, even with the Telegram channel off.

```mermaid
flowchart TD
    Start([Consume AuthEvent.AccountDeletionScheduled]) --> Dedup{idempotencyKey already processed by notifications?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Settings[NotificationSettingsDataSource.getByUserId userId - in a transaction]
    Settings -- none --> Fail([Fails - retried through the notifications DLT topic])
    Settings -- found --> Deliverable{"NotificationTargetDataSource.getEmail userId - same transaction; a real address, not a placeholder?"}
    Deliverable -- Yes --> Channels["Send through every enabled channel plus EMAIL even when it is off, each once - type ACCOUNT is never suppressed"]
    Deliverable -- No, none or placeholder --> ChannelsTelegram["Send through every enabled channel plus EMAIL and TELEGRAM even when they are off, each once"]
    Channels --> Render{"reminder?"}
    ChannelsTelegram --> Render
    Render -- false --> Scheduled["account.deletion_scheduled texts - asked for, date, how to cancel"]
    Render -- true --> Reminder["account.deletion_reminder texts - date, how to cancel"]
    Scheduled --> Ack([Marked processed])
    Reminder --> Ack
```

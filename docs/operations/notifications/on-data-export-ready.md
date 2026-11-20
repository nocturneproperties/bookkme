# React to a ready personal data export

Kafka topic `UserEvent.DataExportReady` → `NotificationEventHandler` → `SendNotification`

Produced by the `user-buildDataExports` job of [Start personal data
export](../user/start-personal-data-export.md), in the transaction that
stores the document. Tells the user their data is ready to download in the
app and until when (`expiresAt`, written in UTC since the user's time zone
isn't known).

The notification type is `ACCOUNT`, which the user can't switch off: it
goes through the channels the user has enabled and always by email, even
with the email channel off. An account with no real email address (an account created through Telegram sign-in, until it sets a real
address) gets it on
Telegram as well, even with the Telegram channel off. The app's
status route (`GET /api/user/export`) reports the same, for a user who
doesn't get the push.

```mermaid
flowchart TD
    Start([Consume UserEvent.DataExportReady]) --> Dedup{idempotencyKey already processed by notifications?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Settings[NotificationSettingsDataSource.getByUserId userId - in a transaction]
    Settings -- none --> Fail([Fails - retried through the notifications DLT topic])
    Settings -- found --> Deliverable{"NotificationTargetDataSource.getEmail userId - same transaction; a real address, not a placeholder?"}
    Deliverable -- Yes --> Channels["Send through every enabled channel plus EMAIL even when it is off, each once - type ACCOUNT is never suppressed"]
    Deliverable -- No, none or placeholder --> ChannelsTelegram["Send through every enabled channel plus EMAIL and TELEGRAM even when they are off, each once"]
    Channels --> Email[EmailNotificationSender - see on-user-updated for the address]
    ChannelsTelegram --> Telegram[TelegramNotificationSender - see on-user-updated for the chat]
    Email --> Ack([Marked processed])
    Telegram --> Ack
```

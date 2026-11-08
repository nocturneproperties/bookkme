# React to a ready business data export

Kafka topic `BusinessEvent.ExportReady` → `NotificationEventHandler` → `SendNotification`

Produced by the `business-buildDataExports` job of [Start business data
export](../business/start-business-data-export.md), in the transaction that
stores the document. Tells the business's owner (`ownerUserId`, read when
the document was stored) that the business's data is ready to download in
the app and until when (`expiresAt`, in UTC).

The notification type is `ACCOUNT`, which the owner can't switch off: it
goes through the channels they have enabled and always by email, even
with the email channel off. The status route
(`GET /api/business/{businessId}/export`) reports the same.

```mermaid
flowchart TD
    Start([Consume BusinessEvent.ExportReady]) --> Dedup{idempotencyKey already processed by notifications?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Settings[NotificationSettingsDataSource.getByUserId ownerUserId - in a transaction]
    Settings -- none --> Fail([Fails - retried through the notifications DLT topic])
    Settings -- found --> Channels["Send through every enabled channel plus EMAIL even when it is off, each once - type ACCOUNT is never suppressed"]
    Channels --> Email[EmailNotificationSender - see on-user-updated for the address]
    Email --> Ack([Marked processed])
```

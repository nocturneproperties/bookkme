# React to user profile update

Kafka topic `UserEvent.Updated` → `NotificationEventHandler` → `UpdateTargetInformation`

Produced by [Update user](../user/update-user.md). Keeps the user's email
delivery target in sync with their profile email; requires notification
settings to already exist (created lazily elsewhere), so an update for a
user with none is treated as a failure rather than silently creating them.

```mermaid
flowchart TD
    Start([Consume UserEvent.Updated]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> GetSettings[GetNotificationSettings userId]
    GetSettings -- not found --> Dlt([Sent to UserEvent.Updated_dlt topic, logged - no auto-retry])
    GetSettings -- found --> UpdateEmail{NotificationTargetDataSource.updateEmail userId email updatedAt - row matched?}
    UpdateEmail -- Yes --> Ack([Marked processed])
    UpdateEmail -- No --> HasEmail{NotificationTargetDataSource.getEmail userId already null?}
    HasEmail -- No, none exists --> Insert[NotificationTargetDataSource.insertEmail userId email updatedAt]
    HasEmail -- Yes, race - inserted concurrently --> Ack
    Insert --> Ack
```

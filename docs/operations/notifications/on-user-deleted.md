# React to user deletion

Kafka topic `AuthEvent.UserDeleted` → `NotificationEventHandler` → `DeleteUserNotificationData`

Produced by [Delete account](../authorization/delete-account.md). Removes
notification preferences and delivery targets (email/Telegram); device
rows themselves are removed separately, per device, by [React to device
deletion](on-device-deleted.md).

```mermaid
flowchart TD
    Start([Consume AuthEvent.UserDeleted]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> DelSettings[NotificationSettingsDataSource.deleteByUserId userId]
    DelSettings --> DelTargets[NotificationTargetDataSource.deleteByUserId userId]
    DelTargets -- throws --> Dlt([Sent to AuthEvent.UserDeleted_dlt topic, logged - no auto-retry])
    DelTargets -- ok --> Ack([Marked processed])
```

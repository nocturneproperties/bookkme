# React to user deletion

Kafka topic `AuthEvent.UserDeleted` → `NotificationEventHandler` → `DeleteUserNotificationData`

Produced by [Delete account](../authorization/delete-account.md), the [`auth-handleDormantAccounts` job](../scheduled-jobs.md) and, after a backup restore, the [`auth-reapplyErasures` job](../scheduled-jobs.md). Removes
notification preferences and delivery targets (email/Telegram); device
rows themselves are removed separately, per device, by [React to device
deletion](on-device-deleted.md).

```mermaid
flowchart TD
    Start([Consume AuthEvent.UserDeleted]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> DelSettings[NotificationSettingsDataSource.deleteByUserId userId]
    DelSettings --> DelTargets[NotificationTargetDataSource.deleteByUserId userId]
    DelTargets --> Shred["SubjectKeys.shred userId - tombstone the key its parked events about the user (as account, employee or client) are sealed under"]
    DelTargets -- throws --> Dlt([Sent to AuthEvent.UserDeleted_dlt topic, logged - no auto-retry])
    Shred -- throws --> Dlt
    Shred -- ok --> Ack([Marked processed])
```

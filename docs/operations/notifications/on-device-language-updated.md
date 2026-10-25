# React to device language change

Kafka topic `AuthEvent.DeviceLanguageUpdated` → `NotificationEventHandler` → `UpdateDeviceLanguage`

Produced by [Verify sign-in](../authorization/sign-in.md) when a
previously-known device signs in again with a different `Accept-Language`.
Keeps the locale used to render push/email notifications current.

```mermaid
flowchart TD
    Start([Consume AuthEvent.DeviceLanguageUpdated]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Update[DeviceDataSource.updateLanguage deviceUuid language]
    Update -- throws --> Dlt([Sent to AuthEvent.DeviceLanguageUpdated_dlt topic, logged - no auto-retry])
    Update -- ok --> Ack([Marked processed])
```

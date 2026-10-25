# React to device deletion

Kafka topic `AuthEvent.DeviceDeleted` → `NotificationEventHandler` → `DeleteDeviceByUUID`

Produced once per device by [Delete account](../authorization/delete-account.md)
(one event per device the deleted account had signed in on). Removes the
device's push-token row.

```mermaid
flowchart TD
    Start([Consume AuthEvent.DeviceDeleted]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Delete[DeviceDataSource.deleteByDeviceUuid deviceUUID]
    Delete -- throws --> Dlt([Sent to AuthEvent.DeviceDeleted_dlt topic, logged - no auto-retry])
    Delete -- ok --> Ack([Marked processed])
```

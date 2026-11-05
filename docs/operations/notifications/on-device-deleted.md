# React to device deletion

`auth.device_deleted` → `NotificationEventHandler` → `DeleteDeviceByUUID`

Produced by [Delete account](../authorization/delete-account.md), and the [inactive-device, dormant-account and erasure-replay jobs](../scheduled-jobs.md). Deletes only the named account's device association. An absent row is already deleted and succeeds. A legacy event with no `authId` is retried and parked for manual reconciliation; it never deletes every account sharing a UUID.

```mermaid
flowchart TD
    Start([Consume AuthEvent.DeviceDeleted]) --> Claim{IdempotentDelivery claim for notifications}
    Claim -- already processed --> Skip([Skip - SKIPPED_DUPLICATE])
    Claim -- claimed --> Scope{authId is not NIL?}
    Scope -- no --> Missing[IllegalArgumentException - account missing]
    Scope -- yes --> Tx[[Begin transaction]]
    Tx --> Delete["DeviceDataSource.deleteByDeviceUuid authId deviceUUID - WHERE auth_id and device_uuid"]
    Delete -- row absent or deleted --> Commit[[Commit]]
    Commit --> Ack([Marked processed])
    Missing --> Retry["auth.device_deleted_notifications_group_dlt - retries then exhausted_event"]
    Delete -- failure --> Retry
```

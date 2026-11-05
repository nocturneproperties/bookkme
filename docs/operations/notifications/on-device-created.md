# React to device creation

`auth.device_created` → `NotificationEventHandler` → `CreateDeviceEntry`

Produced by [Verify sign-up](../authorization/sign-up.md) and [Verify sign-in](../authorization/sign-in.md). Notifications mirrors each `(authId, deviceUuid)` association independently, matching authorization. A replay preserves the row id, current token and language. A conflicting `userId` is rejected instead of changing ownership. Account deletion and later creation have no ordering guarantee; reconcile stale parked creation events against authorization before requesting replay.

```mermaid
flowchart TD
    Start([Consume AuthEvent.DeviceCreated]) --> Claim{IdempotentDelivery claim for notifications}
    Claim -- already processed --> Skip([Skip - SKIPPED_DUPLICATE])
    Claim -- claimed --> Tx[[Begin transaction]]
    Tx --> Create["DeviceDataSource.create authId deviceUUID userId language - UPSERT with id unchanged on duplicate"]
    Create --> Read["Lock and read device WHERE auth_id and device_uuid"]
    Read --> Owner{Stored userId matches event?}
    Owner -- no --> Error[Error.OperationNotAllowed - rollback]
    Owner -- yes --> Commit[[Commit - existing token and language preserved]]
    Commit --> Ack([Marked processed])
    Create -- failure --> Retry["auth.device_created_notifications_group_dlt - retries then exhausted_event"]
    Error --> Retry
```

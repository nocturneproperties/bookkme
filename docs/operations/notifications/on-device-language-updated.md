# React to device language change

`auth.device_language_updated` → `NotificationEventHandler` → `UpdateDeviceLanguage`

Produced by [Verify sign-in](../authorization/sign-in.md) when an existing account/device pair signs in again. Updates only that account's language for the installation. A legacy event with no `authId` is retried and parked for manual reconciliation.

```mermaid
flowchart TD
    Start([Consume AuthEvent.DeviceLanguageUpdated]) --> Claim{IdempotentDelivery claim for notifications}
    Claim -- already processed --> Skip([Skip - SKIPPED_DUPLICATE])
    Claim -- claimed --> Scope{authId is not NIL?}
    Scope -- no --> Missing[IllegalArgumentException - account missing]
    Scope -- yes --> Tx[[Begin transaction]]
    Tx --> Update["DeviceDataSource.updateLanguage authId deviceUuid language - WHERE auth_id and device_uuid"]
    Update -- no matching pair --> NotFound[Error.NotFound - rollback]
    Update -- success --> Commit[[Commit]]
    Commit --> Ack([Marked processed])
    Missing --> Retry["auth.device_language_updated_notifications_group_dlt - retries then exhausted_event"]
    NotFound --> Retry
    Update -- failure --> Retry
```

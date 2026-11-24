# React to device sign-out

`auth.device_signed_out` → `NotificationEventHandler` → `ClearPushNotificationToken`

Produced by [Sign out](../authorization/sign-out.md) and by a reused refresh token in [Refresh access token](../authorization/refresh-token.md). Clears the push token of the named account's device, so a signed-out phone no longer shows that account's notifications. A token registered after `signedOutAt` (the user signed in again and the app sent its token before this event arrived, or the event was retried late) is kept. An absent device has nothing to clear and succeeds.

```mermaid
flowchart TD
    Start([Consume AuthEvent.DeviceSignedOut]) --> Claim{IdempotentDelivery claim for notifications}
    Claim -- already processed --> Skip([Skip - SKIPPED_DUPLICATE])
    Claim -- claimed --> Tx[[Begin transaction]]
    Tx --> Clear["DeviceDataSource.clearTokenRegisteredBefore authId deviceUuid signedOutAt - WHERE auth_id and device_uuid and notification_token_updated_at is null or <= signedOutAt FOR UPDATE"]
    Clear -- no matching row --> Commit[[Commit]]
    Clear -- matched --> Null[notification_token = null, notification_token_updated_at = now]
    Null --> Commit
    Commit --> Ack([Marked processed])
    Clear -- failure --> Retry["auth.device_signed_out_notifications_group_dlt - retries then exhausted_event"]
```

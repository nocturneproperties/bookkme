# Delete account

`DELETE /api/auth/account` → `DeleteAccount`

Requires a fresh passkey assertion of one of the caller's own passkeys as
proof of possession before deleting anything. The sign-in challenge names no
user, so the passkey's owner is compared with the caller
(`FinishAssertion.finishOwnedBy`). Before 0.17.0 it wasn't, and any valid
passkey of any account passed. Every device owned by the auth record is announced as deleted
before the auth record itself is removed, so downstream services (user,
business, notifications) can react and clean up their own data.

```mermaid
flowchart TD
    Start([DELETE /api/auth/account]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Verify[FinishAssertion request]
    Verify -- expired/invalid/owner not found --> R422a([422 CHALLENGE_WINDOW_EXPIRED 6, VERIFICATION_FAILED 4 or PASSKEY_OWNER_NOT_FOUND 7])
    Verify -- ok --> Owner{passkey owner == caller userId?}
    Owner -- No --> R422c([422 PASSKEY_NOT_OWNED 11])
    Owner -- Yes --> GetAuth[AccountDataSource.getAuthRecordByUserId userId]
    GetAuth -- not found --> R422b([422 VERIFICATION_FAILED 4 - Invalid credentials])
    GetAuth -- found --> GetDevices[DeviceDataSource.getDevices authRecord.id]
    GetDevices --> UserDeletedEvt[eventProducer.send AuthEvent.UserDeleted]
    UserDeletedEvt --> DeviceLoop[For each device: send AuthEvent.DeviceDeleted]
    DeviceLoop --> DeleteAuth[AccountEraser - AccountDataSource.deleteAuthorization authRecord.id, cascades to passkeys and devices]
    DeleteAuth --> R204([204 Account deleted])
```

**Consumed by:** `AuthEvent.UserDeleted` fans out to four services —
[appointments](../appointments/on-user-deleted.md),
[business](../business/on-user-deleted.md),
[notifications](../notifications/on-user-deleted.md), and
[user](../user/on-user-deleted.md) (the actual profile delete) — run
independently and in no guaranteed order relative to each other or to this
route's own `deleteAuthorization` write. `AuthEvent.DeviceDeleted` (one per
device) → [notifications: remove the device's push-token
row](../notifications/on-device-deleted.md).

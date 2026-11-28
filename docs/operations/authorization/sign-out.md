# Sign out

`DELETE /api/auth/session` → `SignOut`

Clears the calling device's stored refresh token so it can no longer mint
new access tokens; the device row itself is kept. It also announces the
sign-out, so notifications stops sending pushes to the device (see
[React to device sign-out](../notifications/on-device-signed-out.md)). A
device that no longer exists (deleted after 30 days of inactivity) answers
success and announces nothing, and so does a device that is already signed
out, so a retried sign-out can't clear a push token registered in between.
Only the device row is locked, not the account, so signing out doesn't wait
on sign-ins or refreshes on the account's other devices.

```mermaid
flowchart TD
    Start([DELETE /api/auth/session]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Lock[DeviceDataSource.getDeviceByIdForUpdate principal.deviceId - locks the device row first]
    Lock -- device absent --> R200([200 Success])
    Lock -- found --> SignedIn{device.isSignedIn?}
    SignedIn -- No --> R200
    SignedIn -- Yes --> Delete[DeviceDataSource.deleteTokenFromDevice deviceId]
    Delete --> Event["outbox: AuthEvent.DeviceSignedOut authId deviceUuid signedOutAt"]
    Event --> Commit[[Commit]]
    Commit --> R200
```

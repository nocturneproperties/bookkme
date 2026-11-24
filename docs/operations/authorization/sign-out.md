# Sign out

`DELETE /api/auth/session` → `SignOut`

Clears the calling device's stored refresh token so it can no longer mint
new access tokens; the device row itself is kept. It also announces the
sign-out, so notifications stops sending pushes to the device (see
[React to device sign-out](../notifications/on-device-signed-out.md)). A
device that no longer exists (deleted after 30 days of inactivity) answers
success and announces nothing.

```mermaid
flowchart TD
    Start([DELETE /api/auth/session]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Lock[DeviceDataSource.getDeviceByIdForUpdate principal.deviceId - locks the device row first]
    Lock -- device absent --> R200([200 Success])
    Lock -- found --> Delete[DeviceDataSource.deleteTokenFromDevice deviceId]
    Delete --> Event["outbox: AuthEvent.DeviceSignedOut authId deviceUuid signedOutAt"]
    Event --> Commit[[Commit]]
    Commit --> R200
```

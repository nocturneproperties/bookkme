# Update push notification token

`PUT /api/notification/token/{deviceUuid}` → `UpdatePushNotificationToken`

Requires a valid JWT to reach the handler, but the target device comes
from the path parameter rather than the JWT principal — the handler never
checks that `deviceUuid` belongs to the caller.

```mermaid
flowchart TD
    Start([PUT /api/notification/token/deviceUuid]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Update[DeviceDataSource.updateToken deviceUuid token - not scoped to caller]
    Update --> R200([200 Updated Device])
```

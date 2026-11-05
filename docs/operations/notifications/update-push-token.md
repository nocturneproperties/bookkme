# Update push notification token

`PUT /api/notifications/{deviceUuid}/token` → `UpdatePushNotificationToken`

The route takes `authId` from `AppPrincipal` and the installation UUID from the path. The URL, request body and response remain unchanged. Only the caller's account/device association is updated; another account's association with the same installation is independent. An unknown or foreign device returns 404.

```mermaid
flowchart TD
    Start([PUT /api/notifications/deviceUuid/token]) --> Auth{JWT valid?}
    Auth -- no --> R401([401 Unauthorized])
    Auth -- yes --> Caller[Read AppPrincipal.authId]
    Caller --> Tx[[Begin transaction]]
    Tx --> Update["DeviceDataSource.updateToken authId deviceUuid token - WHERE auth_id and device_uuid"]
    Update -- no matching account and device --> R404([404 Error.NotFound])
    Update -- success --> Commit[[Commit]]
    Commit --> R200([200 Updated Device])
```

# Refresh access token

`POST /api/auth/refresh` (header `Authorization: Bearer <refresh token>`) → `RefreshToken`

Rotates the access/refresh pair for a device. The `Authorization` header is
read directly by the route (not through `AppPrincipal`, since the access
token has typically expired); `GenerateAuthToken` validates the refresh
token against the device's stored hash and issues a new pair.

```mermaid
flowchart TD
    Start([POST /api/auth/refresh]) --> HeaderCheck{Authorization Bearer header present?}
    HeaderCheck -- No --> R401a([401 Unauthorized])
    HeaderCheck -- Yes --> Gen[GenerateAuthToken.invoke Source.RefreshToken token]
    Gen -- token invalid/expired/mismatched hash --> R422([422 INVALID_CREDENTIALS 8])
    Gen -- valid --> Activity[AccountDataSource.recordActivity authId - last_active_at = now and dormancy_stage = ACTIVE, written at most once a day unless a dormancy warning is pending]
    Activity --> Rotate[Rotate device refresh_token_hash + previous_refresh_token_hash]
    Rotate --> R200([200 AuthTokens - new access/refresh pair])
```

Refreshing counts as activity for [dormant-account deletion](../scheduled-jobs.md)
(`auth-handleDormantAccounts`): the app refreshes almost every time it opens,
since access tokens live 5 minutes. Sign-in records it too, through the
same `GenerateAuthToken`, which matters because refresh tokens expire and
inactive devices are deleted after 30 days.

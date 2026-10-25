# Update notification settings

`PUT /api/notification/settings` → `UpdateNotificationSettings`

An upsert keyed on the caller's `userId`: the first call creates the row,
every later call overwrites it. The response strips channels not marked
`availableToClients` before returning.

```mermaid
flowchart TD
    Start([PUT /api/notification/settings]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Upsert[NotificationSettingsDataSource.upsert NotificationSettings userId appointmentEnabled channels]
    Upsert --> Filter[Filter response channels to availableToClients]
    Filter --> R200([200 Updated NotificationSettings])
```

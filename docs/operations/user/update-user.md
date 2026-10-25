# Update user

`PATCH /api/user/me` → `EditUser`

Partial update of the caller's own profile; publishes `UserEvent.Updated`
so other services (business employee records, appointments client
snapshots) can react.

```mermaid
flowchart TD
    Start([PATCH /api/user/me]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Update[UserDataSource.updateUser principal.userId body updatedAt]
    Update -- not found --> R404([404 USER_NOT_EXIST 100001])
    Update -- updated --> Event[eventProducer.send UserEvent.Updated]
    Event --> R200([200 Success])
```

**Consumed by:** `UserEvent.Updated` → [business: sync denormalized client/
employee copies](../business/on-user-updated.md) and [notifications: sync
the email delivery target](../notifications/on-user-updated.md).

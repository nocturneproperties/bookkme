# Store a verified email (internal)

`POST /api/internal/user/{id}/email` → `ChangeUserEmail`

Called by auth once the user confirmed the code sent to their new address
([Confirm an email change](../authorization/confirm-email-change.md)). It is
the only write of `profile.email` after sign-up, and sign-up itself only
accepts a verified address, so every stored email has been verified, except
the never-mailed placeholder of an account created by Telegram sign-in
([Sign in with Telegram](../authorization/telegram-sign-in.md)) or by a
demo ([Start a demo account](../authorization/demo-start.md)). The
[demo upgrade](../authorization/demo-upgrade.md) also sends a name, which
replaces the placeholder first and last name in the same write. The cached
profile is evicted.

```mermaid
flowchart TD
    Start([POST /api/internal/user/id/email<br/>body VerifiedEmailBody email, optional name]) --> Tx[[Begin transaction]]
    Tx --> Change[UserDataSource.changeEmail id email name now - name and last name replaced only when a name is sent]
    Change -- not found --> R404([404 USER_NOT_EXIST 100001])
    Change -- email held by another account,<br/>profile_email_unique --> R422([422 USER_CONTACT_TAKEN 100002])
    Change -- changed --> Event[eventProducer.send UserEvent.Updated]
    Event --> R204([204 No Content])
```

**Consumed by:** `UserEvent.Updated` → [business: sync denormalized client/
employee copies](../business/on-user-updated.md) and [notifications: sync
the email delivery target](../notifications/on-user-updated.md).

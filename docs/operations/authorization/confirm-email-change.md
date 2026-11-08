# Confirm an email change

`POST /api/auth/email/confirm` → `ConfirmEmailChange`

Checks the code from [Request an email change code](start-email-change.md)
against the caller's newest email change and stores the address it was
sent to through the user service. The address comes from the code's
session, never from the body. If the user service refuses the address, the
code is released and can be used again.

```mermaid
flowchart TD
    Start([POST /api/auth/email/confirm<br/>body EmailConfirmation verificationId code]) --> Auth{Signed in?}
    Auth -- No --> R401([401])
    Auth -- Yes --> Redeem[EmailCodeRedeemer.redeem verificationId code ownerId=userId]
    Redeem -- not the caller's newest code,<br/>expired or already used --> R422a([422 EMAIL_CODE_EXPIRED 910002])
    Redeem -- sixth attempt --> R422b([422 EMAIL_CODE_ATTEMPTS_EXCEEDED 910003])
    Redeem -- wrong code, or purpose is not email-change --> R422c([422 EMAIL_CODE_INVALID 910001])
    Redeem -- verified email --> Change[UserClient.changeEmail userId email<br/>internal POST /api/internal/user/id/email]
    Change -- USER_CONTACT_TAKEN, another account took it meanwhile --> R422d([422 EMAIL_EXIST 1, code released])
    Change -- other error --> RErr([Propagate user-service error, code released])
    Change -- stored --> R204([204 No Content, code spent])
```

The user service stores the address and sends `UserEvent.Updated` in one
transaction ([Store a verified email](../user/change-user-email-internal.md)).

**Consumed by:** `UserEvent.Updated` → [business: sync denormalized client/
employee copies](../business/on-user-updated.md) and [notifications: sync
the email delivery target](../notifications/on-user-updated.md).

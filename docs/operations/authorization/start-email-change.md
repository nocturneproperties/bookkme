# Request an email change code

`POST /api/auth/email` → `StartEmailChange`

A signed-in user asks to move their account to a new address. Auth checks
the address is well formed and free, then emails a 6-digit code to it,
valid 10 minutes. Nothing is stored yet: the address changes only through
[Confirm an email change](confirm-email-change.md). The code's owner is
the caller's `userId`, and only their newest code works.

```mermaid
flowchart TD
    Start([POST /api/auth/email<br/>body EmailChangeRequest email<br/>Accept-Language picks the email's language]) --> Auth{Signed in?}
    Auth -- No --> R401([401])
    Auth -- Yes --> EmailFormat{EmailValidator.isValid email?}
    EmailFormat -- No --> R422a([422 INVALID_EMAIL_FORMAT 2])
    EmailFormat -- Yes --> LookupUser[UserClient.getUserByEmail email]
    LookupUser -- found, the caller's own address included --> R422b([422 EMAIL_EXIST 1])
    LookupUser -- error other than 404 --> RErr([Propagate user-service error])
    LookupUser -- 404 not found --> Issue[EmailCodeIssuer.issue email purpose=email-change ownerId=userId<br/>AuthEmails.emailChangeCode language]
    Issue -- code sent to this address in the last 60 s,<br/>or 5 sends this hour --> R429([429 EMAIL_CODE_RESEND_TOO_SOON 910004])
    Issue -- Postmark refuses the address --> R422c([422 EMAIL_UNDELIVERABLE 910005])
    Issue -- sent --> R200([200 EmailVerificationStarted])
```

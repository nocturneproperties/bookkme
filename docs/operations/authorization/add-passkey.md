# Add passkey to account

`POST /api/auth/passkey/add/finish` → `AttachNewPasskeyToAccount`

Second step after `GET /api/auth/passkey/add/challenge`: verifies the
signed challenge and attaches the resulting credential to the currently
authenticated auth record, giving the account another sign-in method.

```mermaid
flowchart TD
    Start([POST /api/auth/passkey/add/finish]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Verify[FinishPasskeyRegistration.verifyRequest request]
    Verify -- expired --> R422a([422 CHALLENGE_WINDOW_EXPIRED 6])
    Verify -- signature invalid --> R422b([422 VERIFICATION_FAILED 4])
    Verify -- ok passkey --> Attach[FinishPasskeyRegistration.attachOwner principal.authId passkey]
    Attach --> R201([201 Passkey created])
```

# Add passkey to account

`POST /api/auth/passkey/add/finish` → `AttachNewPasskeyToAccount`

Second step after `GET /api/auth/passkey/add/challenge`: verifies the
signed challenge and attaches the resulting credential to the currently
authenticated auth record, giving the account another sign-in method.
An account holds at most 10 passkeys (`AttachNewPasskeyToAccount.MAX_PASSKEYS`,
the same for everyone). The account row is locked first, so two additions
can't both take the last slot. The challenge step refuses an account that is
already full with the same `PASSKEYS_LIMIT_REACHED`, before the device asks
the user to create a passkey.

```mermaid
flowchart TD
    Start([POST /api/auth/passkey/add/finish]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Lock[AccountDataSource.lockAccount principal.authId - SELECT FOR UPDATE, first statement]
    Lock -- not found --> R404([404 Error.NotFound])
    Lock -- locked --> Count{PassKeyDataSource.countPasskeys authId >= MAX_PASSKEYS 10?}
    Count -- Yes --> R422l([422 PASSKEYS_LIMIT_REACHED 13])
    Count -- No --> Verify[FinishPasskeyRegistration.verifyRequest request]
    Verify -- missing, expired or already taken challenge --> R422a([422 CHALLENGE_WINDOW_EXPIRED 6])
    Verify -- malformed, signature, challenge or origin invalid --> R422b([422 VERIFICATION_FAILED 4])
    Verify -- ok passkey --> Attach[FinishPasskeyRegistration.attachOwner principal.authId passkey]
    Attach --> R204([204 Passkey created])
```

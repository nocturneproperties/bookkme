# Get sign-up challenge

`POST /api/auth/sign_up/challenge` → `StartRegistration`

The first step of registration: validates the email, confirms no user
already owns it (via the user service), then asks the WebAuthn layer for a
passkey creation challenge. No account row exists yet — that only happens
once [Verify sign-up](sign-up.md) verifies the signed challenge.

```mermaid
flowchart TD
    Start([POST /api/auth/sign_up/challenge]) --> EmailFormat{EmailValidator.isValid email?}
    EmailFormat -- No --> R422a([422 INVALID_EMAIL_FORMAT 200002])
    EmailFormat -- Yes --> LookupUser[UserClient.getUserByEmail email]
    LookupUser -- found --> R422b([422 EMAIL_EXIST 200001])
    LookupUser -- error other than 404 --> RErr([Propagate user-service error])
    LookupUser -- 404 not found --> StartPasskey[StartPasskeyRegistration userHandle=random displayName]
    StartPasskey --> R200([200 RegistrationChallengeResponse])
```

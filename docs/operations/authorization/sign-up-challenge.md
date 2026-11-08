# Get sign-up challenge

`POST /api/auth/passkey/sign_up/challenge` → `StartRegistration`

The first step of registration: validates the email, confirms no user
already owns it (via the user service), asks the WebAuthn layer for a
passkey creation challenge, and emails a 6-digit code to the address. The
code is bound to this challenge (its owner is the challenge's `requestId`)
and lives 10 minutes, as long as the challenge. No account row exists yet:
that only happens once [Verify sign-up](sign-up.md) has checked both the
code and the signed challenge, so every account's email is verified from
the start. Calling it again restarts sign-up with a new challenge and a new
code, subject to the code's resend limits.

```mermaid
flowchart TD
    Start([POST /api/auth/passkey/sign_up/challenge<br/>Accept-Language picks the email's language]) --> EmailFormat{EmailValidator.isValid email?}
    EmailFormat -- No --> R422a([422 INVALID_EMAIL_FORMAT 2])
    EmailFormat -- Yes --> LookupUser[UserClient.getUserByEmail email]
    LookupUser -- found --> R422b([422 EMAIL_EXIST 1])
    LookupUser -- error other than 404 --> RErr([Propagate user-service error])
    LookupUser -- 404 not found --> StartPasskey[StartPasskeyRegistration userHandle=random displayName<br/>challenge cached 10 min under requestId]
    StartPasskey --> Issue[EmailCodeIssuer.issue email purpose=sign-up ownerId=requestId<br/>AuthEmails.signUpCode language]
    Issue -- code sent to this address in the last 60 s,<br/>or 5 sends this hour --> R429([429 EMAIL_CODE_RESEND_TOO_SOON 910004])
    Issue -- Postmark refuses the address --> R422c([422 EMAIL_UNDELIVERABLE 910005])
    Issue -- sent --> R200([200 SignUpChallengeResponse<br/>passkey challenge + emailVerification])
```

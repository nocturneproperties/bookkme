# Upgrade a demo account

`POST /api/auth/demo/upgrade/challenge` → `StartDemoUpgrade`, then
`POST /api/auth/demo/upgrade` → `FinishDemoUpgrade`

Turns a [demo account](demo-start.md) into a regular one that keeps all its
data: the user adds a verified email and a passkey, the placeholder email and
name are replaced, and `authentication.demo_expires_at` is cleared. Both steps
identify the caller the same way (`DemoCallerResolver`, its own transaction):
by the access token while the demo runs (`authenticate(optional = true)`), or,
with no `Authorization` header, by the device's current refresh token in the
body. The refresh token is what keeps the upgrade possible for 7 days after the
demo ended (`DemoAccountPolicy.UPGRADE_GRACE_PERIOD`), when refresh already
answers `DEMO_EXPIRED`; it is only checked, never rotated here. A refresh
token that was rotated out does not count.

The passkey is created for the demo account's own handle (`authentication.uuid`),
so it signs in to this account later. The code email reuses the sign-up text
with its own purpose (`demo-upgrade`), so a sign-up or email change code can't
finish an upgrade.

```mermaid
flowchart TD
    S([POST /api/auth/demo/upgrade/challenge]) --> SEmail{EmailValidator.isValid email?}
    SEmail -- no --> SR422a([422 INVALID_EMAIL_FORMAT 2])
    SEmail -- yes --> SPlace{PlaceholderEmail.isPlaceholder email?}
    SPlace -- yes --> SR422b([422 EMAIL_DOMAIN_UNSUPPORTED 17])
    SPlace -- no --> Resolve
    Resolve[[DemoCallerResolver.resolve - own transaction]] --> Caller{access token sent?}
    Caller -- yes --> ById[DeviceDataSource.getDeviceById deviceId - must belong to authId]
    Caller -- "no, refreshToken in body" --> ByToken[DeviceDataSource.getDeviceByRefreshTokenIdForUpdate - must match the current token, refresh_token_expires_at in the future]
    Caller -- neither --> R401([401])
    ById -- missing or other account --> R401c([401 INVALID_CREDENTIALS 8])
    ByToken -- unknown, rotated out or expired --> R401c
    ById --> Demo{demo_expires_at set?}
    ByToken --> Demo
    Demo -- no --> R422nd([422 NOT_A_DEMO_ACCOUNT 29])
    Demo -- "yes, now past demo end + 7 days" --> R401e([401 DEMO_EXPIRED 27])
    Demo -- yes --> SOwner[UserClient.getUserByEmail email]
    SOwner -- held by another user --> SR422c([422 EMAIL_EXIST 1])
    SOwner -- free, or already this user's from an earlier attempt --> SPasskey[StartPasskeyRegistration handle = authentication.uuid, display name]
    SPasskey --> SCode[EmailCodeIssuer.issue email, purpose demo-upgrade, owner = passkey requestId]
    SCode --> SR200([200 SignUpChallengeResponse])

    F([POST /api/auth/demo/upgrade]) --> FId{requestId a uuid?}
    FId -- no --> FR422a([422 CHALLENGE_WINDOW_EXPIRED 6])
    FId -- yes --> FResolve[[DemoCallerResolver.resolve - as above]]
    FResolve --> Redeem[EmailCodeRedeemer.redeem verificationId code owner = requestId]
    Redeem -- wrong, expired, too many attempts, other purpose --> FR422b([422 EMAIL_CODE_* 910001-910003])
    Redeem -- verified email --> Verify[FinishPasskeyRegistration.verifyRequest]
    Verify -- invalid, or created for another handle --> FR422c([422 VERIFICATION_FAILED 4])
    Verify -- ok --> Profile[UserClient.upgradeDemoProfile userId email name<br/>internal POST /api/internal/user/id/demo_upgrade - publishes UserEvent.Updated]
    Profile -- USER_CONTACT_TAKEN --> FR422d([422 EMAIL_EXIST 1])
    Profile -- ok --> Tx[[Begin transaction]]
    Tx --> Lock[AccountDataSource.getDemoAccountForUpdate authId - locks the account first]
    Lock -- no longer a demo --> FR422e([422 NOT_A_DEMO_ACCOUNT 29])
    Lock -- past demo end + 7 days --> FR401([401 DEMO_EXPIRED 27])
    Lock -- ok --> End[AccountDataSource.endDemo authId - demo_expires_at = NULL]
    End --> Attach[FinishPasskeyRegistration.attachOwner authId passkey]
    Attach --> Evt[eventProducer.send AuthEvent.DemoUpgraded userId]
    Evt --> Token[GenerateAuthToken InitialAuthentication authId deviceUuid - no demo claim, regular refresh token]
    Token --> TxEnd[[Commit]] --> Count[auth_demo_upgraded_total + 1] --> FR200([200 AuthTokens])
```

The profile is stored before the transaction, since it is a call to another
service ([Replace a demo profile](../user/upgrade-demo-profile-internal.md)). When the transaction then fails, the account keeps the real email
and stays a demo; the next attempt may use that same email, which the start
step accepts because it already belongs to this user.

**Consumed by:** `AuthEvent.DemoUpgraded` → [business: clear the demo
flag and limits](../business/on-demo-upgraded.md).

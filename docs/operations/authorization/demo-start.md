# Start a demo account

`POST /api/auth/demo` → `StartDemo`

Creates a 14-day demo account without signing up, like a Telegram sign-in
creates an account: a placeholder user, the `authentication` row and the
device, signed in with normal tokens. The account is a demo because
`authentication.demo_expires_at` is set (now + 14 days); its access tokens
carry the `demo` claim, and the refresh is refused from that date on
([Refresh access token](refresh-token.md)). The user is named `Demo` and gets
the placeholder email `<random hex>@demoundeliverable.org`, which no email is
ever sent to and which sign-up and email change refuse. The device's refresh
token is the only credential until the [upgrade](demo-upgrade.md).

Creation is anonymous, so it is limited twice: nginx's `auth_demo` zone allows
one start a minute from one address (bursts of 3), and auth refuses a new demo
while as many demos are unexpired as `authorization.demo_settings.max_active_accounts`
allows (`NULL` or no row: `DemoAccountPolicy.DEFAULT_MAX_ACTIVE_ACCOUNTS`, 1000;
`0` turns demos off). The cap is read on every request, so an operator's change
applies at once. The count is not serialized, so concurrent starts can end a
few over the cap.

```mermaid
flowchart TD
    Start([POST /api/auth/demo]) --> CountTx[[Transaction]] --> Count[AccountDataSource.countDemoAccountsActiveAt now, DemoSettingsDataSource.getMaxActiveAccounts - stored cap or 1000]
    Count -- at or above the cap --> R429([429 DEMO_UNAVAILABLE 28])
    Count -- below the cap --> CreateUser[UserClient.createUser Demo, empty last name, random at demoundeliverable.org, acceptedDocuments]
    CreateUser -- version not published --> R422c([422 LEGAL_DOCUMENT_VERSION_UNKNOWN 100003])
    CreateUser -- version in effect not accepted --> R422d([422 LEGAL_DOCUMENTS_NOT_ACCEPTED 100004])
    CreateUser -- ok userId --> Tx[[Begin transaction]]
    Tx --> CreateAuth[AccountDataSource.createAuthorization userId, random passkey handle, demo_expires_at = now + 14 days]
    CreateAuth --> NewDevice[DeviceDataSource.insertDevice authId uuid name language]
    NewDevice -- null - already exists --> Fail([throw ACCOUNT_CREATION_FAILED 5])
    NewDevice -- inserted --> DeviceEvt[eventProducer.send AuthEvent.DeviceCreated authId userId deviceUuid language]
    DeviceEvt --> DemoEvt[eventProducer.send AuthEvent.DemoAccountCreated userId expiresAt timeZoneId language]
    DemoEvt --> Token[GenerateAuthToken InitialAuthentication authId deviceUuid - demo claim, refresh token kept until demo end + 7 days]
    Token --> TxEnd[[Commit transaction]] --> Count2[auth_demo_started_total + 1] --> R200([200 AuthTokens])
    Token -- failure --> Rollback
    Fail --> Rollback[[Rollback transaction]]
    Rollback --> CompTx[[Begin compensation transaction]]
    CompTx --> Compensate[eventProducer.send AuthEvent.UserDeleted userId]
    Compensate --> CompEnd[[Commit compensation transaction]]
    CompEnd --> R422e([422 ACCOUNT_CREATION_FAILED 5, or 500 for another failure])
```

**Consumed by:** `AuthEvent.DemoAccountCreated` → [business: seed the sample
business](../business/on-demo-account-created.md); `AuthEvent.DeviceCreated` →
[notifications: mirror the new device](../notifications/on-device-created.md).

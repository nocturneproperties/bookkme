# Sign in with Telegram

`POST /api/auth/telegram/sign_in` → `SignInWithTelegram`

Signs a Telegram Mini App user in with the raw `Telegram.WebApp.initData`. The
data is checked against Telegram's Ed25519 signature for this server's bot
(`APPLICATION_TELEGRAM_BOT_ID`, no bot token needed) and accepted for one hour
after `auth_date`. The account is found by the blind index of the Telegram user
id; an unknown Telegram user gets a new account whose email is the placeholder
`<telegram user id>@telegramundeliverable.org`, which no email is ever sent to.
The Telegram user id also goes to the user service, which stores it encrypted
with the profile and publishes it in `UserEvent.Updated`, so notifications
saves it as the user's Telegram target. The optional `username` of the signed
user object goes along with it, kept only when it is 4 to 32 letters, digits
or underscores. A new account gets it at creation; an existing one has it
synced after every sign-in, since Telegram users can change or remove it, and a
failure there is logged without failing the sign-in.

```mermaid
flowchart TD
    Start([POST /api/auth/telegram/sign_in]) --> Verify[TelegramInitDataVerifier.verify initData]
    Verify -- APPLICATION_TELEGRAM_BOT_ID unset --> R503([503 TELEGRAM_SIGN_IN_UNAVAILABLE 16])
    Verify -- bad signature, other bot, malformed, no user, dated in the future --> R422a([422 TELEGRAM_DATA_INVALID 14])
    Verify -- auth_date older than 1 hour --> R422b([422 TELEGRAM_DATA_EXPIRED 15])
    Verify -- ok TelegramUser --> Find[[Transaction]] --> Lookup[TelegramIdentityDataSource.getAccountByTelegramUser telegramUserId]
    Lookup -- account found --> STx[[Begin transaction]]
    STx --> InsertDevice[DeviceDataSource.insertDevice authId uuid name language]
    InsertDevice -- new device row --> DeviceCreatedEvt[eventProducer.send AuthEvent.DeviceCreated authId userId deviceUuid language]
    InsertDevice -- device already exists --> UpdateLang[DeviceDataSource.updateLanguage authId uuid language]
    UpdateLang --> LangEvt[eventProducer.send AuthEvent.DeviceLanguageUpdated deviceUuid language authId]
    DeviceCreatedEvt --> GenToken[GenerateAuthToken InitialAuthentication authId deviceUuid]
    LangEvt --> GenToken
    GenToken --> SyncName[UserClient.changeTelegramUsername userId username or null]
    SyncName -- failure logged, ignored --> R200a([200 AuthTokens])
    SyncName -- ok --> R200a
    Lookup -- no account --> Docs{acceptedDocuments sent?}
    Docs -- no --> R422f([422 LEGAL_DOCUMENTS_NOT_ACCEPTED 100004])
    Docs -- yes --> CreateUser[UserClient.createUser first_name, last_name or empty, telegramUserId at telegramundeliverable.org, acceptedDocuments, telegramUserId, username]
    CreateUser -- version not published --> R422c([422 LEGAL_DOCUMENT_VERSION_UNKNOWN 100003])
    CreateUser -- version in effect not accepted --> R422d([422 LEGAL_DOCUMENTS_NOT_ACCEPTED 100004])
    CreateUser -- ok userId --> Tx[[Begin transaction]]
    Tx --> CreateAuth[AccountDataSource.createAuthorization userId, random passkey handle]
    CreateAuth --> Link[TelegramIdentityDataSource.link authId telegramUserId]
    Link --> NewDevice[DeviceDataSource.insertDevice authId uuid name language]
    NewDevice -- null - already exists --> Fail([throw ACCOUNT_CREATION_FAILED 5])
    NewDevice -- inserted --> NewDeviceEvt[eventProducer.send AuthEvent.DeviceCreated authId userId deviceUuid language]
    NewDeviceEvt --> NewToken[GenerateAuthToken InitialAuthentication authId deviceUuid]
    NewToken --> TxEnd[[Commit transaction]] --> R200b([200 AuthTokens])
    Link -- telegram_id_index taken by a concurrent sign-up --> Rollback
    NewToken -- failure --> Rollback
    Fail --> Rollback[[Rollback transaction]]
    Rollback --> CompTx[[Begin compensation transaction]]
    CompTx --> Compensate[eventProducer.send AuthEvent.UserDeleted userId]
    Compensate --> CompEnd[[Commit compensation transaction]]
    CompEnd --> R422e([422 ACCOUNT_CREATION_FAILED 5, or 500 for another failure])
```

An existing account ignores `acceptedDocuments`; a client sends them after a
`LEGAL_DOCUMENTS_NOT_ACCEPTED` answer, once the user accepted the documents in
effect. A user deleted with their account, or by dormancy, leaves no
`telegram_identity` row (`ON DELETE CASCADE`), so signing in again creates a new
account.

The username sync is [Store the Telegram username](../user/change-telegram-username-internal.md),
which publishes `UserEvent.Updated` only when the username changed.

**Consumed by:** `AuthEvent.DeviceCreated` → [notifications: mirror the new
device](../notifications/on-device-created.md). `AuthEvent.DeviceLanguageUpdated`
→ [notifications: update the device's locale](../notifications/on-device-language-updated.md).
The compensating `AuthEvent.UserDeleted` fans out to [user](../user/on-user-deleted.md),
[business](../business/on-user-deleted.md), [appointments](../appointments/on-user-deleted.md)
and [notifications](../notifications/on-user-deleted.md), as for [Verify sign-up](sign-up.md).

# Verify sign-in

`POST /api/auth/sign_in` → `SignIn`

Verifies the assertion produced against the challenge from `GET
/api/auth/passkey/sign_in/challenge`, then either registers this device as
new or just refreshes its language if it already signed in before — either
way a token pair is minted for it.

```mermaid
flowchart TD
    Start([POST /api/auth/sign_in]) --> Tx[[Begin transaction]]
    Tx --> Verify[FinishAssertion request]
    Verify -- owner not found --> R422a([422 PASSKEY_OWNER_NOT_FOUND 300003])
    Verify -- expired --> R422b([422 CHALLENGE_WINDOW_EXPIRED 300001])
    Verify -- signature invalid --> R422c([422 VERIFICATION_FAILED 300002])
    Verify -- ok credentials --> InsertDevice[DeviceDataSource.insertDevice ownerId uuid name language]
    InsertDevice -- new device row --> DeviceCreatedEvt[eventProducer.send AuthEvent.DeviceCreated]
    InsertDevice -- device already exists --> UpdateLang[DeviceDataSource.updateLanguage ownerId uuid language]
    UpdateLang --> LangEvt[eventProducer.send AuthEvent.DeviceLanguageUpdated]
    DeviceCreatedEvt --> GenToken[GenerateAuthToken InitialAuthentication ownerId deviceUuid]
    LangEvt --> GenToken
    GenToken --> R200([200 AuthTokens])
```

**Consumed by:** `AuthEvent.DeviceCreated` → [notifications: mirror the new
device](../notifications/on-device-created.md). `AuthEvent.DeviceLanguageUpdated`
→ [notifications: update the device's locale](../notifications/on-device-language-updated.md).

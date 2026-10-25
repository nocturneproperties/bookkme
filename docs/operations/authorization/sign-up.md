# Verify sign-up (create account)

`POST /api/auth/sign_up` → `FinishRegistration`

Verifies the signed WebAuthn challenge from [Get sign-up
challenge](sign-up-challenge.md), then creates the user (in the user
service), the local `Authentication` record, the passkey credential, the
first device, and the initial token pair — all inside one transaction. If
anything inside the transaction fails, the already-created remote user and
device are rolled back via compensating `UserDeleted`/`DeviceDeleted`
events rather than a distributed transaction.

```mermaid
flowchart TD
    Start([POST /api/auth/sign_up]) --> Verify[FinishPasskeyRegistration.verifyRequest request]
    Verify -- expired --> R422a([422 CHALLENGE_WINDOW_EXPIRED 300001])
    Verify -- signature invalid --> R422b([422 VERIFICATION_FAILED 300002])
    Verify -- ok --> CreateUser[UserClient.createUser name lastName email]
    CreateUser -- already exists --> R422c([422 USER_ALREADY_EXIST 200003])
    CreateUser -- ok userId --> Tx[[Begin transaction]]
    Tx --> CreateAuth[AccountDataSource.createAuthorization Authentication]
    CreateAuth --> AttachOwner[FinishPasskeyRegistration.attachOwner ownerId passkey]
    AttachOwner --> InsertDevice[DeviceDataSource.insertDevice authId uuid name language]
    InsertDevice -- null - already exists --> Fail1([throw ACCOUNT_CREATION_FAILED 200004])
    InsertDevice -- inserted --> DeviceEvent[eventProducer.send AuthEvent.DeviceCreated]
    DeviceEvent --> GenToken[GenerateAuthToken InitialAuthentication ownerId deviceUuid]
    GenToken --> TxEnd[[Commit transaction]]
    TxEnd --> R200([200 AuthTokens])
    Fail1 --> Compensate[Send AuthEvent.UserDeleted + AuthEvent.DeviceDeleted]
    Compensate --> R422d([422 ACCOUNT_CREATION_FAILED 200004])
```

**Consumed by:** `AuthEvent.DeviceCreated` → [notifications: mirror the new
device](../notifications/on-device-created.md). The compensating
`AuthEvent.UserDeleted`/`AuthEvent.DeviceDeleted` sent on failure fan out to
the same consumers as [Delete account](delete-account.md)'s "Consumed by"
section, even though no account ever successfully existed here.

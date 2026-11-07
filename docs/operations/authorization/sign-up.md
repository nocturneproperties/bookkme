# Verify sign-up (create account)

`POST /api/auth/sign_up` → `FinishRegistration`

Verifies the signed WebAuthn challenge from [Get sign-up
challenge](sign-up-challenge.md), then creates the user (in the user
service, through [Create user (internal)](../user/create-user-internal.md),
which also records the legal document versions the app sent in
`acceptedDocuments`. The field is required: an older app that sends none
gets 400. It must name the version in effect of every published document,
otherwise the user service answers 422 `LEGAL_DOCUMENTS_NOT_ACCEPTED`
(100004), or `LEGAL_DOCUMENT_VERSION_UNKNOWN` (100003) for a version that
isn't published or in effect, and nothing is created), the local `Authentication` record, the passkey credential, the
first device, and the initial token pair — all inside one transaction. If
anything inside the transaction fails, the local device and its `DeviceCreated` outbox row roll back together. The already-created remote user is removed through a compensating `UserDeleted` event rather than a distributed transaction. Events go through the
[transactional outbox](../../transactional-outbox.md), which only accepts a
send inside a transaction, so the compensating events are stored in a
transaction of their own, opened after the failed one has rolled back.

```mermaid
flowchart TD
    Start([POST /api/auth/sign_up]) --> Body{Body has acceptedDocuments?}
    Body -- No --> R400([400])
    Body -- Yes --> Verify[FinishPasskeyRegistration.verifyRequest request]
    Verify -- expired --> R422a([422 CHALLENGE_WINDOW_EXPIRED 6])
    Verify -- signature invalid --> R422b([422 VERIFICATION_FAILED 4])
    Verify -- ok --> CreateUser[UserClient.createUser name lastName email acceptedDocuments]
    CreateUser -- already exists --> R422c([422 USER_ALREADY_EXIST 3])
    CreateUser -- version not published --> R422e([422 LEGAL_DOCUMENT_VERSION_UNKNOWN 100003])
    CreateUser -- version in effect not accepted --> R422f([422 LEGAL_DOCUMENTS_NOT_ACCEPTED 100004])
    CreateUser -- ok userId --> Tx[[Begin transaction]]
    Tx --> CreateAuth[AccountDataSource.createAuthorization Authentication]
    CreateAuth --> AttachOwner[FinishPasskeyRegistration.attachOwner ownerId passkey]
    AttachOwner --> InsertDevice[DeviceDataSource.insertDevice authId uuid name language]
    InsertDevice -- null - already exists --> Fail1([throw ACCOUNT_CREATION_FAILED 5])
    InsertDevice -- inserted --> DeviceEvent[eventProducer.send AuthEvent.DeviceCreated authId deviceUuid userId language]
    DeviceEvent --> GenToken[GenerateAuthToken InitialAuthentication ownerId deviceUuid]
    GenToken --> TxEnd[[Commit transaction]]
    GenToken -- failure --> Rollback
    TxEnd --> R200([200 AuthTokens])
    Fail1 --> Rollback[[Rollback transaction - DeviceCreated outbox row discarded]]
    Rollback --> CompTx[[Begin compensation transaction]]
    CompTx --> Compensate[eventProducer.send AuthEvent.UserDeleted userId]
    Compensate --> CompEnd[[Commit compensation transaction]]
    CompEnd --> R422d([422 ACCOUNT_CREATION_FAILED 5])
```

**Consumed by:** `AuthEvent.DeviceCreated` → [notifications: mirror the new
device](../notifications/on-device-created.md). The compensating `AuthEvent.UserDeleted` sent on failure fans out to [user](../user/on-user-deleted.md), [business](../business/on-user-deleted.md), [appointments](../appointments/on-user-deleted.md) and [notifications](../notifications/on-user-deleted.md), as for [Delete account](delete-account.md), even though no account ever successfully existed here. No device-deletion event is sent on failure: the device insert and creation event never committed, and cleanup by installation UUID could affect another account.

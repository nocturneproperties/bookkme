# Start personal data export

`POST /api/user/export` → `StartPersonalDataExport`, then the
`user-buildDataExports` job → `BuildDataExports` with
`PersonalDataExportAssembler`

Queues a JSON copy of everything Bookk holds about the caller, built in the
background and kept 72 hours. Lifecycle, storage and the document's
sections: [Data export](../../data-export.md).

The export must be confirmed with a passkey: the body carries an action confirmation
token from [Confirm a sensitive action with a
passkey](../authorization/action-confirmation.md), issued for this caller, the action
`user.export` (`EXPORT_PERSONAL_DATA`), the nil business id and the caller's
own user id as target. `ActionConfirmationGate.spend` checks it and spends its `jti`
once before the transaction; any failure after that gives the token back,
so a refused start (429) doesn't burn it. An app that sends no body gets
the same 403.

The two read routes don't mutate and have no diagram: `GET /api/user/export`
(the caller's `DataExport`: status, request time, expiry, when the next one
can start) and `GET /api/user/export/download` (the JSON file, 404 while
none is ready).

```mermaid
flowchart TD
    Start([POST /api/user/export body DataExportRequest]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> ActionConfirmation{"ActionConfirmationGate.spend - verify body.confirmationToken caller user.export NIL caller, reserve jti - SET NX"}
    ActionConfirmation -- missing, expired, used or for another action or user --> R403([403 USER_ACTION_CONFIRMATION_INVALID 100005])
    ActionConfirmation -- spent --> Tx[[Begin transaction]]
    Tx --> Lock["DataExports.start - DataExportDataSource.getForUpdate userId - SELECT FOR UPDATE"]
    Lock -- no row --> Insert[DataExportDataSource.insert userId now - PENDING]
    Lock -- PENDING or RUNNING --> Same[Return the export being built]
    Lock -- READY requested under a day ago --> R429([429 DATA_EXPORT_TOO_FREQUENT 900002 - token given back])
    Lock -- older READY, or FAILED --> Restart[DataExportDataSource.restart userId now - PENDING, document cleared]
    Insert --> Profile
    Restart --> Profile
    Same --> Profile
    Profile{"UserDataExportDataSource.getProfile userId - database only, never the cache"}
    Profile -- null --> R404([404 Error.NotFound - rolled back, token given back])
    Profile -- found --> R200([200 DataExport RUNNING - token stays spent])

    Job([user-buildDataExports every 10 s]) --> Abandon["DataExportDataSource.failAbandoned now - RUNNING, lease over, 3 attempts - FAILED"]
    Abandon --> Queue["DataExportDataSource.claimable now - PENDING, or RUNNING with lease over, up to 20"]
    Queue --> Claim{"DataExportDataSource.claim userId claimId now+10 min - conditional UPDATE, attempts+1"}
    Claim -- another instance claimed it --> Next([Next export])
    Claim -- claimed --> Own["PersonalDataExportAssembler.assemble - transaction: UserDataExportDataSource.getProfile, LegalDocumentDataSource.getAcceptances"]
    Own --> Calls["Outside a transaction - GET /api/internal/export/auth, business, appointments, notifications /userId"]
    Calls -- any call fails --> GiveBack["DataExportDataSource.giveBack userId claimId - PENDING while attempts remain, else FAILED until now+72 h"]
    Calls -- all parts --> Gzip[Encode PersonalDataExport as JSON, gzip]
    Gzip --> Store{"Transaction - DataExportDataSource.complete userId claimId document now now+72 h - only while the claim is held"}
    Store -- too large or database error --> GiveBack
    Store -- row deleted or claimed by another build --> Next
    Store -- stored --> Ready["eventProducer.send UserEvent.DataExportReady userId expiresAt - outbox row in the same transaction"]
```

**Consumed by:** `UserEvent.DataExportReady` → [notifications: tell the user](../notifications/on-data-export-ready.md).

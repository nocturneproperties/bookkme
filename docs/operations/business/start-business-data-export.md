# Start business data export

`POST /api/business/{businessId}/export` → `StartBusinessDataExport`, then
the `business-buildDataExports` job → `BuildDataExports` with
`BusinessDataExportAssembler`

Queues a JSON copy of everything the business holds, for its owner only:
what the business needs to move to another system or to answer its own
clients' access requests, and what Bookk returns when it leaves (Art.
28(3)(g)). Built in the background and kept 72 hours. Lifecycle, storage
and the document's sections: [Data export](../../data-export.md).

Only the owner may export (`BusinessDataSource.isOwner`); an employee
permission is not enough, since the document holds every client at once.
Anyone else gets 404, like every permission failure. The owner check runs
twice: in its own transaction before the step-up token is spent, so a
non-owner's token is not burned and a non-owner never learns whether the
business exported today (429), and again right after the export row is
locked, so an owner who lost the business while waiting is refused.

The export must be confirmed with a passkey: the body carries a step-up
token from [Confirm a sensitive action with a
passkey](../authorization/step-up.md), issued for this caller, the action
`business.export` (`EXPORT_BUSINESS_DATA`) and this business as both
business and target. `StepUpGate.spend` checks it and spends its `jti`
once; any failure after that gives the token back.

The read routes don't mutate and have no diagram: `GET
/api/business/{businessId}/export` (the business's `DataExport`) and `GET
/api/business/{businessId}/export/download` (the JSON file
`bookk-business-data.json`, 404 while none is ready), both owner only.

```mermaid
flowchart TD
    Start([POST /api/business/businessId/export body DataExportRequest]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Owner{"Transaction - BusinessDataSource.isOwner caller businessId"}
    Owner -- No --> R404([404 Error.OperationNotAllowed - token not spent])
    Owner -- Yes --> StepUp{"StepUpGate.spend - verify body.stepUpToken caller business.export businessId businessId, reserve jti - SET NX"}
    StepUp -- missing, expired, used or for another action or business --> R403([403 BUSINESS_STEP_UP_INVALID 200040])
    StepUp -- spent --> Tx[[Begin transaction]]
    Tx --> Lock["DataExports.start - DataExportDataSource.getForUpdate businessId - SELECT FOR UPDATE"]
    Lock -- no row --> Insert[DataExportDataSource.insert businessId now - PENDING]
    Lock -- PENDING or RUNNING --> Same[Return the export being built]
    Lock -- READY requested under a day ago --> R429([429 DATA_EXPORT_TOO_FREQUENT 900002 - token given back])
    Lock -- older READY, or FAILED --> Restart[DataExportDataSource.restart businessId now - PENDING, document cleared]
    Insert --> Recheck
    Restart --> Recheck
    Same --> Recheck
    Recheck{"BusinessDataSource.isOwner caller businessId - after the lock"}
    Recheck -- No --> R404b([404 Error.OperationNotAllowed - rolled back, token given back])
    Recheck -- Yes --> R200([200 DataExport RUNNING - token stays spent])

    Job([business-buildDataExports every 10 s]) --> Claim{"DataExportDataSource.claim businessId claimId now+10 min - conditional UPDATE, attempts+1"}
    Claim -- another instance claimed it --> Next([Next export])
    Claim -- claimed --> Own["BusinessDataExportAssembler.assemble - transaction: getBusinessById, getServiceGroups, getServices, getEmployees, BusinessDataExportDataSource.getPendingInvitations, getClients"]
    Own -- business gone --> GiveBack
    Own --> Calls["Outside a transaction - GET /api/internal/export/appointments/business/businessId"]
    Calls -- fails --> GiveBack["DataExportDataSource.giveBack businessId claimId - PENDING while attempts remain, else FAILED until now+72 h"]
    Calls -- ok --> Gzip[Encode BusinessDataExport as JSON, gzip]
    Gzip --> Store{"Transaction - DataExportDataSource.complete businessId claimId document now now+72 h - only while the claim is held"}
    Store -- too large or database error --> GiveBack
    Store -- row deleted or claimed by another build --> Next
    Store -- stored --> Ready["eventProducer.send BusinessEvent.ExportReady businessId ownerUserId expiresAt - outbox row in the same transaction"]
```

The abandoned-build and claimable steps before the claim are the same as in
[Start personal data export](../user/start-personal-data-export.md).

**Consumed by:** `BusinessEvent.ExportReady` → nothing yet. The push
notification that tells the owner the export is ready is the next step of
#139 (notifications).

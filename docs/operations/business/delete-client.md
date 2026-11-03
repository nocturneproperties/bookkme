# Delete client

`DELETE /api/business/{businessId}/clients/{id}` → `DeleteClient`

Deletes the business's client row and announces it with
`BusinessEvent.ClientDeleted`, which erases the client from the business's
appointments. Anyone with the `CLIENTS` delete grant may delete a client
without an account. A client with an account (an integrated client, `user_id`
set) can be deleted only by the owner. Their row mirrors a real user, and
deleting it erases that user's booking history at this business. Their
account and their other businesses are untouched.

The deletion must be confirmed with a passkey: the request body carries a
step-up token from [Confirm a sensitive action with a
passkey](../authorization/step-up.md), issued for this caller, the action
`client.delete`, this business and this client. `StepUpVerifier` checks its
signature (auth's keys, step-up audience), expiry and claims, then
`StepUpDataSource.reserve` spends its `jti` once (Valkey `SET NX` on
`step_up:<jti>` until the token expires). If the deletion then fails for any
reason, the reservation is released, so a rejected call doesn't burn the
token. An app that sends no body gets the same 403, so it knows to ask for
the passkey.

The client row is locked first (`SELECT … FOR UPDATE`), before the permission
lookup, so a concurrent booking takeover can't turn the row into an
integrated client between the check and the delete. The takeover waits, finds
the row gone and creates a new one instead.

```mermaid
flowchart TD
    Start([DELETE /api/business/businessId/clients/id]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> StepUp{"StepUpVerifier.verify body.stepUpToken caller client.delete businessId id"}
    StepUp -- missing, expired, wrong audience or claims --> R403step([403 BUSINESS_STEP_UP_INVALID 200040])
    StepUp -- valid --> Reserve{"StepUpDataSource.reserve jti remaining lifetime - SET NX"}
    Reserve -- already used --> R403step
    Reserve -- reserved --> Tx[[Begin transaction]]
    Tx --> Lock["ClientDataSource.getClientForUpdate businessId id - SELECT FOR UPDATE"]
    Lock --> Suspended{BusinessPermissionDataSource.getPermission - caller is a suspended employee of the business?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller CLIENTS.delete?}
    Perm -- No --> R404a([404 Error.OperationNotAllowed])
    Perm -- Yes --> Found{client found in the business?}
    Found -- No --> R404b([404 BUSINESS_CLIENT_NOT_EXISTS])
    Found -- Yes --> Kind{client has an account?}
    Kind -- Yes --> Owner{"BusinessDataSource.isOwner caller businessId"}
    Owner -- No --> R404c([404 Error.OperationNotAllowed])
    Owner -- Yes --> Delete
    Kind -- No --> Delete[ClientDataSource.deleteClient businessId id]
    Delete --> Record["ErasedClientDataSource.record businessId id - INSERT IGNORE erased_client, so a backup restore can delete it again"]
    Record --> Send["eventProducer.send BusinessEvent.ClientDeleted businessId clientId - outbox row in this transaction"]
    Send --> R204([204 Entity deleted - token stays spent])
    Tx -- any failure after the reservation --> Release["StepUpDataSource.release jti - NonCancellable, best effort"]
```

**Consumed by:** `BusinessEvent.ClientDeleted` → [appointments: erase the client](../appointments/on-client-deleted.md).

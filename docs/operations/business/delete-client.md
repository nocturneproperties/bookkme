# Delete client

`DELETE /api/business/{businessId}/clients/{id}` → `DeleteClient`

Deletes the business's client row and announces it with
`BusinessEvent.ClientDeleted`, which erases the client from the business's
appointments. Anyone with the `CLIENTS` delete grant may delete a client
without an account. A client with an account (an integrated client, `user_id`
set) can be deleted only by the owner. Their row mirrors a real user, and
deleting it erases that user's booking history at this business. Their
account and their other businesses are untouched.

The client row is locked first (`SELECT … FOR UPDATE`), before the permission
lookup, so a concurrent booking takeover can't turn the row into an
integrated client between the check and the delete. The takeover waits, finds
the row gone and creates a new one instead.

```mermaid
flowchart TD
    Start([DELETE /api/business/businessId/clients/id]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
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
    Delete --> Send["eventProducer.send BusinessEvent.ClientDeleted businessId clientId - outbox row in this transaction"]
    Send --> R204([204 Entity deleted])
```

**Consumed by:** `BusinessEvent.ClientDeleted` → [appointments: erase the client](../appointments/on-client-deleted.md).

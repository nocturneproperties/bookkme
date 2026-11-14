# React to user profile update

Kafka topic `UserEvent.Updated` → `BusinessEventHandlerImpl` → `SyncUserProfile`

Produced by [Create user (internal)](../user/create-user-internal.md),
[Update user](../user/update-user.md) and [Store a verified email](../user/change-user-email-internal.md).
A new user has no integrated client or employee rows yet, so the event
from sign-up finds nothing to merge or update. Business keeps
denormalized copies of a user's name/last name/email/phone on any
`Client.Integrated` or `Employee` row linked to that `userId` (so listing
clients/employees doesn't need a live call to the user service); this
keeps those copies from going stale.

An email is unique per business across every client, so a new email can
collide with a detached client of a business where the user is already
integrated: the same person, entered by the business before they used the
app with another address. The email is verified before it reaches the
user's profile, so the merge goes to its owner; a phone is not verified and
not unique per business, so it never merges anything. `findClientMergesForUser`
finds those rows (only through integrated rows the event is newer than,
the same `source_updated_at` guard the update uses), each one is deleted
as a duplicate, and one `BusinessEvent.ClientLinked` per kept client lists
the duplicates merged into it, so appointments moves their history onto
the user's client and records the account on it. Both happen before the
update applies the new values.
The user service keeps emails unique between users, so two integrated rows
only collide while two users swap an email and the second update arrives
first; that delivery fails on the unique index and its
retry succeeds once the first one is applied.

```mermaid
flowchart TD
    Start([Consume UserEvent.Updated]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Find[ClientDataSource.findClientMergesForUser userId email updatedAt]
    Find --> Merge["ClientDataSource.deleteClients every fromClientId - one DELETE, none without merges; then per kept client - eventProducer.send BusinessEvent.ClientLinked businessId toClientId userId mergedClientIds"]
    Merge --> UpdClients[ClientDataSource.updateIntegratedClients userId name lastName email phone updatedAt]
    UpdClients --> UpdEmployees[EmployeeDataSource.updateIntegratedEmployees userId name lastName email phone updatedAt]
    UpdEmployees --> Shred["SubjectKeys.shredAll the merged fromClientIds - last"]
    Shred -- throws --> Dlt([Sent to UserEvent.Updated_dlt topic, logged - no auto-retry])
    Shred -- ok --> Ack([Marked processed])
```

**Consumed by:** `BusinessEvent.ClientLinked` → [appointments: move the duplicates' history and give the client's appointments the account](../appointments/on-client-linked.md).

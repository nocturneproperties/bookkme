# React to a client getting an account

Kafka topic `BusinessEvent.ClientLinked` → `AppointmentEventHandler` → `LinkClientUser`

Says: client row `clientId` of the business belongs to `userId`, and the
rows `mergedClientIds` were deleted as its duplicates. Produced by:

- [Get appointment booking context](../business/get-appointment-booking-context.md),
  when a user's first booking takes over the detached client of the
  business holding their email (no merged ids);
- [React to user profile update (business)](../business/on-user-updated.md),
  when the user's new email belongs to a detached client of a
  business where they are already a client, which are deleted as
  duplicates of their row (one event per kept row).

Appointments and requests store the business's client row id as
`client_id`, and appointments booked for a row while it had no account
have `client_user_id` empty. In one transaction this moves every row of
the duplicates onto `clientId`, then records the account on all of the
kept client's appointments and requests, so that [account
deletion](on-user-deleted.md), which erases by `client_user_id`, finds
them. The stored name, phone, email and note are a snapshot of booking
time and keep their values, but the moved rows are re-encrypted under the
kept client's key before the duplicates' keys are shredded. Moving and linking travel in one event, because two
events on separate topics could arrive in either order and leave moved rows
without the account.

`BusinessEvent.ClientLinked` and `AuthEvent.UserDeleted` still travel on
separate topics with no ordering between them. If the user deleted their
account right after the event was published, the deletion may be handled
first and find nothing on these rows. The deletion records the user in
`deleted_user` as its first statement, and this reads it first with a
shared lock:

- If the deletion has committed, the record is there, and the rows just
  moved and linked are erased in the same transaction
  (`DeleteUserAppointmentData`: cancel the upcoming ones and tell their
  employees, clear the details, delete the requests).
- If the deletion is still running, the read waits for it, then sees the
  record.
- If the deletion starts later, its insert waits for this transaction, and
  then it erases these rows by `client_user_id` itself.

`DeletedUserLockMariaDbTest` pins both waits against a real MariaDB.

`BusinessEvent.ClientDeleted` has the same problem: business can link a row
and the owner delete it right after, and the deletion may be handled first.
So this also reads `deleted_client` for `clientId` with a shared lock, and
when the client is already deleted it erases the rows it just moved and
linked (`DeleteClientAppointmentData`, see [React to client
deletion](on-client-deleted.md)). `DeletedClientLockMariaDbTest` pins those
waits.

```mermaid
flowchart TD
    Start([Consume BusinessEvent.ClientLinked]) --> Claim{"IdempotentDelivery.claim event:appointments:topic:idempotencyKey"}
    Claim -- already processed --> Skip([Skip - SKIPPED_DUPLICATE])
    Claim -- held by another delivery --> Wait[Re-claim every second, up to the 1-minute claim TTL] --> Claim
    Claim -- claimed --> Tx[[Begin transaction]]
    Tx --> Deleted["DeletedUserDataSource.isDeletedForShare userId - LOCK IN SHARE MODE"]
    Deleted --> ClientDeleted["DeletedClientDataSource.isDeletedForShare businessId clientId - LOCK IN SHARE MODE"]
    ClientDeleted --> Move["AppointmentDataSource.reassignClients + AppointmentRequestDataSource.reassignClients businessId mergedIds clientId - per table, lock the merged rows FOR UPDATE, decrypt each under its old client key and store it under clientId's key, none without merged ids"]
    Move --> LinkA["AppointmentDataSource.linkClientUser businessId clientId userId - client_user_id = userId"]
    LinkA --> LinkR["AppointmentRequestDataSource.linkClientUser businessId clientId userId"]
    LinkR --> Was{account already deleted?}
    Was -- No --> WasClient
    Was -- Yes --> Erase["DeleteUserAppointmentData userId - same transaction, see on-user-deleted"]
    Erase --> WasClient{client already deleted?}
    WasClient -- No --> ShredMerged
    WasClient -- Yes --> EraseClient["DeleteClientAppointmentData businessId clientId - same transaction, see on-client-deleted"]
    EraseClient --> ShredMerged["SubjectKeys.shredAll merged ids - last, business deleted those client rows"]
    ShredMerged --> Processed([markProcessed - kept 1 hour])
    Erase -- throws, rolled back --> Release[Release the claim]
    EraseClient -- throws, rolled back --> Release
    LinkR -- throws, rolled back --> Release
    Release --> Dlt["Retried through business.client_linked_appointments_dlt, up to 3 attempts"]
    Dlt -- every attempt failed --> Parked(["Saved to exhausted_event - EventsExhausted alert, replayed on the next start or when replay_requested is set"])
```

**Consumed by:**
- `AppointmentEvent.CancelledForDeletedClient`, sent by either erasure when the account or the client was already deleted → [notifications: tell the employee](../notifications/on-appointment-cancelled-for-deleted-client.md).
- `AppointmentEvent.Cancelled`, sent by the client erasure to a client with an account → [notifications: tell the client](../notifications/on-appointment-cancelled.md).

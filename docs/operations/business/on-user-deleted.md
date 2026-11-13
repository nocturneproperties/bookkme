# React to user deletion

Kafka topic `AuthEvent.UserDeleted` → `BusinessEventHandlerImpl` → `DeleteBusiness` + `DeleteUserBusinessData`

Produced by [Delete account](../authorization/delete-account.md), the [`auth-handleDormantAccounts` job](../scheduled-jobs.md) and, after a backup restore, the [`auth-reapplyErasures` job](../scheduled-jobs.md). Two
steps, and only the first one gates the second: `deleteBusiness` is
unwrapped with `.getOrThrow()` inline, so if it fails the handler throws
immediately and `deleteUserBusinessData` never runs; `deleteUserBusinessData`'s
own `Result` is the lambda's return value, so *its* failure still reaches
the framework's dead-letter handling, just after the deletes below have
already been attempted.

The user's client and employee rows are deleted, not anonymized: nothing in
business needs them once the account is gone, and the appointments booked
for them keep their own erased copy of the details. Appointments handles the
same event itself and cancels the user's upcoming appointments in both
roles ([React to user deletion (appointments)](../appointments/on-user-deleted.md)).

```mermaid
flowchart TD
    Start([Consume AuthEvent.UserDeleted]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> DeleteBiz[DeleteBusiness userId]
    DeleteBiz --> GetBiz[BusinessDataSource.deleteUserBusinesses userId - returns owned business ids]
    GetBiz --> Loop["DataExports.discardAll owned business ids - one SELECT FOR UPDATE and one DELETE of their data_export rows, SubjectKeys.shredAll their key_ids in one upsert; then per business eventProducer.send BusinessEvent.Deleted businessId"]
    Loop -- consumed by --> Downstream[[See appointments/on-business-deleted.md]]
    Loop -- deleteBusiness throws --> Dlt([Sent to AuthEvent.UserDeleted_dlt topic, logged - no auto-retry])
    Loop --> DeleteData[[DeleteUserBusinessData userId - one transaction]]
    DeleteData --> DelClients["ClientDataSource.deleteClientsByUserId userId - SELECT business_id, id FOR UPDATE WHERE user_id, then DELETE them, in every business; returns the erased ids"]
    DelClients --> DelEmployees["EmployeeDataSource.deleteEmployeesByUserId userId - SELECT business_id FOR UPDATE WHERE user_id, then DELETE them; services, working hours, days off and permission grants go by ON DELETE CASCADE; returns the business ids"]
    DelEmployees --> DiscardExports["DataExports.discardAll distinct business ids of the erased clients and employees - one SELECT FOR UPDATE, one DELETE, SubjectKeys.shredAll in one upsert"]
    DelEmployees -- throws --> Dlt
    DiscardExports -- ok --> Ack([Marked processed])
```

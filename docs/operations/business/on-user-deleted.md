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
    DeleteBiz --> GetBiz["BusinessDataSource.getUserBusinessIdsForUpdate userId, then ClientDataSource.getClientIdsOfBusinessesForUpdate - SELECT the owned business ids, then their client ids, FOR UPDATE"]
    GetBiz --> DelBiz["BusinessDataSource.deleteBusinesses owned business ids - one DELETE, clients go by ON DELETE CASCADE"]
    DelBiz --> Loop["DataExports.discardAll owned business ids - one SELECT FOR UPDATE and one DELETE of their data_export rows, SubjectKeys.shredAll their key_ids in one upsert; then per business eventProducer.send BusinessEvent.Deleted businessId; SubjectKeys.shredAll the selected client ids last"]
    Loop -- consumed by --> Downstream[[See appointments/on-business-deleted.md]]
    Loop -- deleteBusiness throws --> Dlt([Sent to AuthEvent.UserDeleted_dlt topic, logged - no auto-retry])
    Loop --> DeleteData[[DeleteUserBusinessData userId - one transaction]]
    DeleteData --> SelClients["ClientDataSource.getClientsOfUserForUpdate userId and EmployeeDataSource.getBusinessIdsOfUserForUpdate userId - SELECT business_id, id and the employee business ids FOR UPDATE WHERE user_id"]
    SelClients --> DelClients["ClientDataSource.deleteClients the selected ids - one DELETE, in every business"]
    DelClients --> DelEmployees["EmployeeDataSource.deleteEmployeesByUserId userId - DELETE; services, working hours, days off and permission grants go by ON DELETE CASCADE"]
    DelEmployees --> DiscardExports["DataExports.discardAll distinct business ids of the selected clients and employees - one SELECT FOR UPDATE, one DELETE, SubjectKeys.shredAll in one upsert"]
    DiscardExports --> ShredUser["SubjectKeys.shredAll userId and the selected client ids - last"]
    DelEmployees -- throws --> Dlt
    ShredUser -- ok --> Ack([Marked processed])
```

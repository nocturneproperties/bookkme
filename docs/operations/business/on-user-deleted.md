# React to user deletion

Kafka topic `AuthEvent.UserDeleted` → `BusinessEventHandlerImpl` → `DeleteBusiness` + `AnonymizeUserProfile`

Produced by [Delete account](../authorization/delete-account.md). Two
steps, and only the first one gates the second: `deleteBusiness` is
unwrapped with `.getOrThrow()` inline, so if it fails the handler throws
immediately and `anonymizeUserProfile` never runs; `anonymizeUserProfile`'s
own `Result` is the lambda's return value, so *its* failure still reaches
the framework's dead-letter handling, just after the deletes below have
already been attempted.

```mermaid
flowchart TD
    Start([Consume AuthEvent.UserDeleted]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> DeleteBiz[DeleteBusiness userId]
    DeleteBiz --> GetBiz[BusinessDataSource.deleteUserBusinesses userId - returns owned business ids]
    GetBiz --> Loop[For each owned business: eventProducer.send BusinessEvent.Deleted businessId]
    Loop -- consumed by --> Downstream[[See appointments/on-business-deleted.md]]
    Loop -- deleteBusiness throws --> Dlt([Sent to AuthEvent.UserDeleted_dlt topic, logged - no auto-retry])
    Loop --> Anonymize[AnonymizeUserProfile userId]
    Anonymize --> AnonClients[ClientDataSource.anonymizeClientsByUserId userId]
    AnonClients --> AnonEmployees[EmployeeDataSource.anonymizeEmployeesByUserId userId]
    AnonEmployees --> DelPerm[BusinessPermissionDataSource.deleteUserPermissions userId]
    DelPerm -- throws --> Dlt
    DelPerm -- ok --> Ack([Marked processed])
```

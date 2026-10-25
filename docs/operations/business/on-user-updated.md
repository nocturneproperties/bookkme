# React to user profile update

Kafka topic `UserEvent.Updated` → `BusinessEventHandlerImpl` → `SyncUserProfile`

Produced by [Update user](../user/update-user.md). Business keeps
denormalized copies of a user's name/last name/email/phone on any
`Client.Integrated` or `Employee` row linked to that `userId` (so listing
clients/employees doesn't need a live call to the user service); this
keeps those copies from going stale.

```mermaid
flowchart TD
    Start([Consume UserEvent.Updated]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> UpdClients[ClientDataSource.updateIntegratedClients userId name lastName email phone updatedAt]
    UpdClients --> UpdEmployees[EmployeeDataSource.updateIntegratedEmployees userId name lastName email phone updatedAt]
    UpdEmployees -- throws --> Dlt([Sent to UserEvent.Updated_dlt topic, logged - no auto-retry])
    UpdEmployees -- ok --> Ack([Marked processed])
```

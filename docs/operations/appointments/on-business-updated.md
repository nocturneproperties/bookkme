# React to business update

Kafka topic `BusinessEvent.Updated` → `AppointmentEventHandler` → `UpdateBusinessInformation`

Produced by [Update business](../business/update-business.md). Keeps the
appointments module's cached `BusinessSnapshot` (name, address, time zone,
schedule) in sync so date/time validation and cancellation notifications
use current business data without an inline call to the business service.

```mermaid
flowchart TD
    Start([Consume BusinessEvent.Updated]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Update[AppointmentSubscriptionDataSource.updateBusiness snapshot updatedAt]
    Update -- throws --> Dlt([Sent to BusinessEvent.Updated_dlt topic, logged - no auto-retry])
    Update -- ok --> Ack([Marked processed])
```

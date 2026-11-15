# React to business update

Kafka topic `BusinessEvent.Updated` → `AppointmentEventHandler` → `UpdateBusinessInformation`

Produced by [Update business](../business/update-business.md). Keeps the
appointments module's cached `BusinessSnapshot` (name, address, time zone,
schedule) in sync so date/time validation and cancellation notifications
use current business data without an inline call to the business service.
It also keeps `owner_user_id` (`BusinessDTO.ownerUserId`), which account
deletion uses to find the businesses a user owns, so that a future business
transfer moves it. An update never creates a business row.

```mermaid
flowchart TD
    Start([Consume BusinessEvent.Updated]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Update["AppointmentSubscriptionDataSource.updateBusiness snapshot updatedAt - applied only when newer than source_updated_at; owner_user_id = business.ownerUserId"]
    Update -- throws --> Dlt([Sent to BusinessEvent.Updated_dlt topic, logged - no auto-retry])
    Update -- ok --> Ack([Marked processed])
```

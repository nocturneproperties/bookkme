# React to an appointment cancelled by a client's account deletion

Kafka topic `AppointmentEvent.CancelledForDeletedClient` → `NotificationEventHandler` → `SendNotification`

Produced by [React to user deletion (appointments)](../appointments/on-user-deleted.md),
once per upcoming appointment it cancelled because the client deleted their
account. The same erasure also runs from [React to a client getting an
account](../appointments/on-client-linked.md) when the account turns out to
be deleted already. Tells the employee assigned to the appointment that it
was cancelled and the time is free again. The event carries no client
name: the client's details are already erased when it is sent.

```mermaid
flowchart TD
    Start([Consume AppointmentEvent.CancelledForDeletedClient]) --> Claim{"IdempotentDelivery.claim event:notifications:topic:idempotencyKey"}
    Claim -- already processed --> Skip([Skip - SKIPPED_DUPLICATE])
    Claim -- held by another delivery --> Wait[Re-claim every second, up to the 1-minute claim TTL] --> Claim
    Claim -- claimed --> Send[SendNotification to=employeeUserId]
    Send --> GetSettings[transaction - NotificationSettingsDataSource.getByUserId employeeUserId]
    GetSettings -- found --> TypeAllowed{settings.appointmentEnabled?}
    TypeAllowed -- No --> Suppressed([markProcessed - notification suppressed])
    TypeAllowed -- Yes --> Channels["For each enabled channel, outside that transaction - senderMap.channel.send employeeUserId notification"]
    Channels --> Processed([markProcessed - kept 1 hour])
    GetSettings -- not found or throws --> Release[Release the claim] --> Dlt["Retried through appointment.cancelled_for_deleted_client_notifications_dlt, up to 3 attempts"]
    Dlt -- every attempt failed --> Parked([Saved to exhausted_event - replayed only by hand])
```

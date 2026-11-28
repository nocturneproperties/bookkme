# React to an appointment cancelled by a client's deletion

Kafka topic `AppointmentEvent.CancelledForDeletedClient` → `NotificationEventHandler` → `SendNotification`

Produced once per upcoming appointment cancelled because its client deleted
their account:

- by [React to user deletion (appointments)](../appointments/on-user-deleted.md);
- by [React to a client getting an account](../appointments/on-client-linked.md),
  which runs that erasure when the account turns out to be deleted already.

Tells the employee assigned to the appointment that it was cancelled and the
time is free again (`appointment.cancelled_for_deleted_client.*`, in every
channel). A client the business removes itself is not announced to the
business ([React to client deletion](../appointments/on-client-deleted.md)
tells only the client). The event carries no client name: the client's
details are already erased when it is sent.

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
    Dlt -- every attempt failed --> Parked(["Saved to exhausted_event - EventsExhausted alert, replayed on the next start or when replay_requested is set"])
```

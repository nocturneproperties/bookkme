# React to appointment cancellation

Kafka topic `AppointmentEvent.Cancelled` → `NotificationEventHandler` → `SendNotification`

Produced by [Cancel appointment](../appointments/cancel-appointment.md), and by [React to client deletion](../appointments/on-client-deleted.md) for a client with an account, with the reason "Client was removed by the business", and by [React to user deletion](../appointments/on-user-deleted.md) for a client with an account whose employee deleted theirs, with the reason "Employee deleted their account".
Notifies the client their appointment was cancelled.

```mermaid
flowchart TD
    Start([Consume AppointmentEvent.Cancelled]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> HasAccount{clientUserId set?}
    HasAccount -- "No, the client has no account" --> NoOne([Marked processed - nobody to notify])
    HasAccount -- Yes --> Send[SendNotification to=clientUserId]
    Send --> GetSettings[transaction - NotificationSettingsDataSource.getByUserId clientUserId]
    GetSettings -- not found --> Dlt([Sent to AppointmentEvent.Cancelled_dlt topic, logged - no auto-retry])
    GetSettings -- found --> TypeAllowed{settings.appointmentEnabled?}
    TypeAllowed -- No --> Ack([Marked processed - notification suppressed])
    TypeAllowed -- Yes --> Channels[For each enabled channel, outside that transaction: senderMap.channel.send clientUserId notification]
    Channels --> Ack2([Marked processed])
```

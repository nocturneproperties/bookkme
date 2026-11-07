# React to appointment request decline

Kafka topic `AppointmentEvent.RequestRejected` → `NotificationEventHandler` → `SendNotification`

Produced by [Decline appointment request](../appointments/decline-appointment-request.md).
Notifies the client their request was declined.

```mermaid
flowchart TD
    Start([Consume AppointmentEvent.RequestRejected]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> HasAccount{clientUserId set?}
    HasAccount -- "No, the client has no account" --> NoOne([Marked processed - nobody to notify])
    HasAccount -- Yes --> Send[SendNotification to=clientUserId]
    Send --> GetSettings[transaction - NotificationSettingsDataSource.getByUserId clientUserId]
    GetSettings -- not found --> Dlt([Sent to AppointmentEvent.RequestRejected_dlt topic, logged - no auto-retry])
    GetSettings -- found --> TypeAllowed{settings.appointmentEnabled?}
    TypeAllowed -- No --> Ack([Marked processed - notification suppressed])
    TypeAllowed -- Yes --> Channels[For each enabled channel, outside that transaction: senderMap.channel.send clientUserId notification]
    Channels --> Ack2([Marked processed])
```

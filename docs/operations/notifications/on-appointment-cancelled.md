# React to appointment cancellation

Kafka topic `AppointmentEvent.Cancelled` → `NotificationEventHandler` → `SendNotification`

Produced by [Cancel appointment](../appointments/cancel-appointment.md).
Notifies the client their appointment was cancelled.

```mermaid
flowchart TD
    Start([Consume AppointmentEvent.Cancelled]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Send[SendNotification to=clientUserId]
    Send --> GetSettings[NotificationSettingsDataSource.getByUserId clientUserId]
    GetSettings -- not found --> Dlt([Sent to AppointmentEvent.Cancelled_dlt topic, logged - no auto-retry])
    GetSettings -- found --> TypeAllowed{settings.appointmentEnabled?}
    TypeAllowed -- No --> Ack([Marked processed - notification suppressed])
    TypeAllowed -- Yes --> Channels[For each enabled channel: senderMap.channel.send clientUserId notification]
    Channels --> Ack2([Marked processed])
```

# React to appointment request approval

Kafka topic `AppointmentEvent.RequestApproved` → `NotificationEventHandler` → `SendNotification`

Produced from two places that share the same underlying approval code:
[Create appointment from a pending
request](../appointments/create-appointment-from-request.md), and the
automatic-approval branch inside [Create appointment
request](../appointments/create-appointment-request.md) (when the business
has `automaticApproval` on). Notifies the client their request was
accepted.

```mermaid
flowchart TD
    Start([Consume AppointmentEvent.RequestApproved]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Send[SendNotification to=clientUserId]
    Send --> GetSettings[NotificationSettingsDataSource.getByUserId clientUserId]
    GetSettings -- not found --> Dlt([Sent to AppointmentEvent.RequestApproved_dlt topic, logged - no auto-retry])
    GetSettings -- found --> TypeAllowed{settings.appointmentEnabled?}
    TypeAllowed -- No --> Ack([Marked processed - notification suppressed])
    TypeAllowed -- Yes --> Channels[For each enabled channel: senderMap.channel.send clientUserId notification]
    Channels --> Ack2([Marked processed])
```

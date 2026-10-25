# React to appointment request creation

Kafka topic `AppointmentEvent.RequestCreated` → `NotificationEventHandler` → `SendNotification`

Produced by [Create appointment request](../appointments/create-appointment-request.md)
(the manual-approval path only — see [Send
notification](#shared-send-notification-behavior) below). Notifies the
employee a new request is waiting for them.

```mermaid
flowchart TD
    Start([Consume AppointmentEvent.RequestCreated]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Send[SendNotification to=employeeUserId]
    Send --> GetSettings[NotificationSettingsDataSource.getByUserId employeeUserId]
    GetSettings -- not found --> Dlt([Sent to AppointmentEvent.RequestCreated_dlt topic, logged - no auto-retry])
    GetSettings -- found --> TypeAllowed{settings.appointmentEnabled?}
    TypeAllowed -- No --> Ack([Marked processed - notification suppressed])
    TypeAllowed -- Yes --> Channels[For each enabled channel: senderMap.channel.send employeeUserId notification]
    Channels --> Ack2([Marked processed])
```

### Shared `SendNotification` behavior

Every `AppointmentEvent.*` and `BusinessEvent.EmployeeInvitation*` reaction
below funnels through the same `SendNotification` operation: look up the
recipient's settings, skip silently if the notification type is disabled
(appointment notifications are user-suppressible; employee-invitation ones
are not — see [Invite employee](../business/create-employee-invitation.md)'s
diagram), then fan out to every channel (push/email/etc.) the recipient has
enabled.

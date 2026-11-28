# React to a resolved appointment request

Kafka topic `AppointmentEvent.RequestResolved` → `NotificationEventHandler` → `SendNotification.data`

Produced whenever a pending request stops being pending: [approved](../appointments/create-appointment-from-request.md),
[declined](../appointments/decline-appointment-request.md),
[withdrawn](../appointments/withdraw-appointment-request.md), cancelled as
outdated by the [`appointments-deleteOutdatedRequests` job](../scheduled-jobs.md),
or deleted with its [client](../appointments/on-client-deleted.md) or the
[client's account](../appointments/on-user-deleted.md). The event carries only
the request id and the assigned employee's user id.

It sends a data-only push to every device of the employee that has a push
token, so the app can remove the request's "new appointment request" push
wherever it is still shown ([Push notifications](../../push-notifications.md)).
It follows the settings that decided whether the request push was shown: it
is sent only when the employee has appointment notifications and the push
channel on. It never goes by email or Telegram.

```mermaid
flowchart TD
    Start([Consume AppointmentEvent.RequestResolved]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> GetSettings[transaction - NotificationSettingsDataSource.getByUserId employeeUserId]
    GetSettings -- not found --> Dlt([Sent to AppointmentEvent.RequestResolved_dlt topic, logged - no auto-retry])
    GetSettings -- found --> Allowed{settings.appointmentEnabled and push channel enabled?}
    Allowed -- No --> Suppressed([Marked processed - nothing sent])
    Allowed -- Yes --> Devices[transaction - DeviceDataSource.getByUserId employeeUserId]
    Devices --> Each[For each device with a push token, outside that transaction]
    Each --> Send["FirebaseMessaging.send - data type appointment_request_resolved and appointmentRequestId, no notification, Android priority normal, APNs content-available 1, apns-push-type background, apns-priority 5"]
    Send -- UNREGISTERED --> Clear[transaction - DeviceDataSource.updateToken null]
    Send -- other failure --> Log[Log error]
    Send -- ok --> Ack([Marked processed])
    Clear --> Ack
    Log --> Ack
```

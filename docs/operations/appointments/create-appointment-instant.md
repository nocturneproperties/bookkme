# Create instant appointment

`POST /api/appointments/instant` → `CreateAppointment` (`isInstant = true`)

Books a slot directly without going through the request/approval flow.
Restricted to self-booking: the caller must be the appointment's own
client, checked via `userId == appointment.userId` before anything else runs.
No request-approval event fires here since there was never a pending
request. That self-check is this operation's only authorization gate —
there is no business-permission check, since the caller is the client
booking with the business, not a staff member.

```mermaid
flowchart TD
    Start([POST /api/appointments/instant body Appointment]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> SelfCheck{userId == appointment.userId?}
    SelfCheck -- No --> R422a([422 INSTANT_APPOINTMENT_ONLY_SELF_ALLOWED])
    SelfCheck -- Yes --> Tx[[Begin transaction]]
    Tx --> Settings[AppointmentSettingsDataSource.getForUpdate businessId]
    Settings -- not found --> R404a([404 Error.NotFound])
    Settings -- found --> PastCheck{appointment.date < now?}
    PastCheck -- Yes --> R422b([422 DATE_IN_PAST 300012])
    PastCheck -- No --> WorkdayCheck{date within business workday?}
    WorkdayCheck -- No --> R422c([422 DATE_NOT_ALLOWED 300003])
    WorkdayCheck -- Yes --> WorktimeCheck{slot within worktime?}
    WorktimeCheck -- No --> R422d([422 TIME_NOT_ALLOWED 300002])
    WorktimeCheck -- Yes --> Overlap{overlaps another appointment?}
    Overlap -- Yes --> R422e([422 APPOINTMENT_EXISTS 300004])
    Overlap -- No --> Create[AppointmentDataSource.create appointment]
    Create --> R200([200 Created Appointment])
```

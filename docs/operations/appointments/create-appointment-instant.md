# Create instant appointment

`POST /api/appointments/instant` → `CreateAppointment` (`isInstant = true`)

Books a slot directly without going through the request/approval flow.
Restricted to self-booking: the caller must be the appointment's own
client, checked via `userId == appointment.initiatorUserId` before anything else runs.
The stored client account (`client.userId`, `client_user_id`) is set to the
caller, whatever the body says.
No request-approval event fires here since there was never a pending
request. That self-check is this operation's only authorization gate —
there is no business-permission check, since the caller is the client
booking with the business, not a staff member.

A booking from an account whose deletion appointments has already handled
is rejected: it would otherwise store the deleted person's details after
the erasure ran. The check reads `deleted_user` with a shared lock as the
transaction's first statement, so a deletion running at the same moment
either finishes first (and the booking is rejected) or waits for the
booking to commit and then erases it by `client_user_id`.

```mermaid
flowchart TD
    Start([POST /api/appointments/instant body Appointment]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> SelfCheck{userId == appointment.initiatorUserId?}
    SelfCheck -- No --> R422a([422 INSTANT_APPOINTMENT_ONLY_SELF_ALLOWED])
    SelfCheck -- Yes --> Own["appointment.client.userId = userId"] --> Tx[[Begin transaction]]
    Tx --> Deleted["DeletedUserDataSource.isDeletedForShare userId - LOCK IN SHARE MODE, first statement"]
    Deleted -- deleted --> R404del([404 Error.OperationNotAllowed - the account was deleted])
    Deleted -- not deleted --> Settings[AppointmentSettingsDataSource.getForUpdate businessId]
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

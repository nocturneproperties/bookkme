# Mark appointment as no-show

`POST /api/appointments/{id}/no-show` → `MarkAppointmentNoShow`

Records that the client missed the appointment. Only an appointment that has
already started can be marked, and it may be either still `SCHEDULED` or
already `COMPLETED` by staff through [Complete appointment](complete-appointment.md)
or by the automatic end-of-day job.
Marking a `COMPLETED` appointment as no-show clears its `completedBy`.
Both statuses can be changed only within 7 days of the appointment's end.
A no-show may be corrected back to completed through the completion route.
Cancelled appointments remain terminal and must be replaced by a new booking.
`UNRESOLVED` appointments are immutable and return
`APPOINTMENT_EDIT_WINDOW_CLOSED` (300026).
Marking an appointment that is already `NO_SHOW` succeeds unchanged. The
operation reads the appointment with `getForUpdate` (`SELECT … FOR UPDATE`)
as its first read, checks status and start time itself, and only then calls
the unconditional `markNoShow` write, so a concurrent cancel cannot slip in
between.

The business is taken from the stored appointment, never from the request.
The appointment is fetched before the permission check so a `view`-only
employee can be let through when it's their own — see [Managing your own
resource on a `view`
grant](../../object-permissions.md#managing-your-own-resource-on-a-view-grant).

```mermaid
flowchart TD
    Start([POST /api/appointments/id/no-show]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Get[AppointmentDataSource.getForUpdate id - row lock]
    Get -- missing --> R404a([404 Error.NotFound])
    Get -- found --> Suspended{AppointmentPermissionDataSource.getPermission appointment.businessId - grant row marked suspended?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller update permission, or view permission and appointment.employee.userId == userId?}
    Perm -- No --> R404b([404 Error.OperationNotAllowed])
    Perm -- Yes --> Status{appointment.status}
    Status -- SCHEDULED, COMPLETED or NO_SHOW --> Window{appointment dateEnd + 7 days before now?}
    Status -- UNRESOLVED --> R422w
    Status -- CANCELLED --> R422a([422 ALREADY_CANCELLED 300005])
    Window -- Yes --> R422w([422 APPOINTMENT_EDIT_WINDOW_CLOSED 300026])
    Window -- No --> Started{already NO_SHOW or date_start is not after now?}
    Started -- No --> R422b([422 NOT_STARTED 300018])
    Started -- Yes, already NO_SHOW --> R200u([200 Appointment unchanged])
    Started -- Yes --> Mark[AppointmentDataSource.markNoShow id - sets NO_SHOW and clears completedBy]
    Mark --> R200([200 No-show Appointment])
```

No event is published.

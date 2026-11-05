# Update appointment (reschedule)

`PUT /api/appointments/{id}` → `UpdateAppointment`

Edits a `SCHEDULED`, `COMPLETED` or `NO_SHOW` appointment within its 7-day
edit window — date, note, assigned employee and services. `CANCELLED` is
terminal; staff must create a new appointment rather than restore it.
`UNRESOLVED` is immutable and returns `APPOINTMENT_EDIT_WINDOW_CLOSED` (300026).
The body (`AppointmentUpdate`) carries only ids: `employeeId` and
`services` as `RequestedService(serviceId, count)`. No name, contact or price
is ever taken from the client:

- the **client** and **business** always stay those of the stored
  appointment — an update can neither swap the client nor move the
  appointment into another business;
- an **unchanged employee** keeps the stored `EmployeeSnapshot`; a new one is
  resolved from the business service;
- a **service already on the appointment** keeps its stored `ServiceSnapshot`,
  including its price, so rescheduling never silently re-prices a booking;
  only **newly added** services are resolved from the catalog.

When nothing needs resolving (a plain date/note change) no call leaves the
service. Otherwise one call, `BusinessClient.getAppointmentRescheduleContext`
(`POST /api/internal/business/{id}/appointment-reschedule-context`), resolves
the changed employee and the added services together. Unlike the booking
context it never resolves or creates a client.

Changing the date or assigned employee reopens an eligible appointment as
`SCHEDULED`, clears `completedBy` and removes its previous checkout adjustment
and additional checkout services. Edits that keep the date and employee preserve
the recorded status, completion attribution and checkout, even when the original
date is in the past. A changed time or employee must use a future date. Other status changes happen through [Cancel appointment](cancel-appointment.md),
[Complete appointment](complete-appointment.md) and [Mark appointment as
no-show](mark-appointment-no-show.md), or the `MarkAppointmentsCompleted`
job. The response is the stored appointment, not an echo of the body.

It runs in two transactions, so that no lock is held during the call to
business and the booking transaction still takes its locks before its first
plain read:

1. **Check.** A short transaction reads the appointment
   (`AppointmentDataSource.get`), checks the caller's permission, that it is
   still eligible (`Appointment.requireEditable()`) and that a changed date or
   employee does not move the booking into the past. The appointment is fetched before the permission check
   so a `view`-only employee can be let through when it's their own — see
   [Managing your own resource on a `view`
   grant](../../object-permissions.md#managing-your-own-resource-on-a-view-grant).
   The change is then resolved from business, outside any transaction.
2. **Book.** The settings row is locked first
   (`AppointmentSettingsDataSource.getForUpdate`), then the appointment row
   (`getForUpdate`). If the locked appointment differs from the one checked
   in step 1 (a cancel, completion, no-show, job run or another reschedule
   committed in between), nothing is written and the route answers 409
   `TRANSACTION_CONFLICT`, so the app retries with fresh data. Otherwise the
   permission, status and date are checked again, now that the snapshot is
   taken after both locks. A resulting `SCHEDULED` booking is checked for workday,
   worktime and overlaps. Historical corrections that preserve `COMPLETED` or
   `NO_SHOW` reserve no slot and skip those booking checks. The appointment is
   then written.

The settings lock serializes every booking write of the business and is the
double-booking guard — the `(user_id, business_id, date_start)` index is
deliberately non-unique so a cancelled appointment doesn't block its slot.
Before this order, the permission was read before the settings lock, so a
reschedule that waited behind another booking checked overlaps against a
snapshot from before that booking committed and could take the same slot.
Lock order settings → appointment can't deadlock with the other writers: a
cancel, completion or no-show locks only the appointment, and approving a
request locks the request, then the settings. Nothing is written until every
validation has passed.

```mermaid
flowchart TD
    Start([PUT /api/appointments/id]) --> PathCheck{path id == body.id?}
    PathCheck -- No --> R400([400 Bad Request])
    PathCheck -- Yes --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Selection{services non-empty, distinct and every count > 0?}
    Selection -- No --> R422v([422 SERVICE_SELECTION_INVALID 300024])
    Selection -- Yes --> Tx1[[Transaction 1 - check]]
    Tx1 --> Get[AppointmentDataSource.get id]
    Get -- missing --> R404a([404 Error.NotFound])
    Get -- found --> Check
    subgraph Check [requireReschedulable - run in both transactions]
        Suspended{AppointmentPermissionDataSource.getPermission businessId - grant row marked suspended?}
        Suspended -- No --> Perm{caller update permission, or view permission and stored employee.userId == userId?}
        Perm -- Yes --> Stored{Appointment.requireEditable - stored status}
        Stored -- SCHEDULED, COMPLETED or NO_SHOW --> Window{stored dateEnd + 7 days before now?}
        Window -- No --> Past{date or employee changed and body.date before now?}
    end
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Perm -- No --> R404b([404 Error.OperationNotAllowed])
    Stored -- CANCELLED --> R422c([422 ALREADY_CANCELLED 300005])
    Stored -- UNRESOLVED --> R422w
    Window -- Yes --> R422w([422 APPOINTMENT_EDIT_WINDOW_CLOSED 300026])
    Past -- Yes --> R422p([422 DATE_IN_PAST 300012])
    Past -- No, in transaction 1 --> Changes{employeeId changed or a service id not on the appointment?}
    Changes -- No --> Rebuild
    Changes -- Yes --> Resolve[BusinessClient.getAppointmentRescheduleContext businessId changedEmployeeId addedServiceIds - no transaction, no lock]
    Resolve -- employee missing --> R404e([404 BUSINESS_EMPLOYEE_NOT_EXISTS 200024])
    Resolve -- employee suspended --> R422es([422 BUSINESS_EMPLOYEE_SUSPENDED 200033])
    Resolve -- service missing or foreign --> R422sv([422 BUSINESS_QUOTE_SERVICE_NOT_FOUND 200013])
    Resolve -- resolved --> Rebuild[rescheduled = checked copy with date, note, employee snapshot, services expanded per count - stored snapshots reused, keep outcome and checkout unless date or employee changed]
    Rebuild --> Tx2[[Transaction 2 - book]]
    Tx2 --> Settings[AppointmentSettingsDataSource.getForUpdate businessId - first lock]
    Settings -- missing --> R404s([404 Error.NotFound])
    Settings -- found --> Lock[AppointmentDataSource.getForUpdate id - second lock]
    Lock --> Same{locked appointment == the one checked in transaction 1?}
    Same -- No --> R409([409 TRANSACTION_CONFLICT 900001 - nothing written])
    Same -- Yes, check again --> Check
    Past -- No, in transaction 2 --> Booking{resulting status is SCHEDULED?}
    Booking -- No --> Write
    Booking -- Yes --> Workday{settings.isInWorkday date?}
    Workday -- No --> R422d([422 DATE_NOT_ALLOWED 300003])
    Workday -- Yes --> Worktime{settings.isInWorktime date, dateEnd?}
    Worktime -- No --> R422t([422 TIME_NOT_ALLOWED 300002])
    Worktime -- Yes --> Overlap{AppointmentDataSource.hasOverlapsWith rescheduled?}
    Overlap -- Yes --> R422o([422 APPOINTMENT_EXISTS 300004])
    Overlap -- No --> Write[AppointmentDataSource.update rescheduled - replaces services]
    Write --> Visit[eventProducer.send AppointmentEvent.ClientVisitBooked businessId client.id rescheduled dateEnd]
    Visit --> R200([200 Stored Appointment])
```

**Consumed by:** `AppointmentEvent.ClientVisitBooked` → [business: record the client's
visit](../business/on-client-visit-booked.md).

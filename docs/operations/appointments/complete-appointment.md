# Complete appointment

`POST /api/appointments/{id}/complete` → `CompleteAppointment`

Records that the appointment took place, with `completedBy = USER`. This is
how a business that switched `automaticCompletion` off in its appointment
settings closes its appointments — the `MarkAppointmentsCompleted` job skips
those businesses (see [Scheduled (recurring) jobs](../scheduled-jobs.md)).
It works for businesses with automatic completion on as well, e.g. to close an
appointment during the 7-day edit window. The job completes appointments once their local end day has passed.

A `SCHEDULED` or `NO_SHOW` appointment that has already started can be
completed. This lets staff correct an accidental no-show. `CANCELLED` is
terminal and returns `ALREADY_CANCELLED` (300005); create a new appointment
instead of restoring it.
Completing an appointment that is already `COMPLETED` succeeds unchanged
within the edit window, keeping its existing `completedBy`. Once more than
7 days have passed since the appointment's end, completion and price adjustments
are refused with `APPOINTMENT_EDIT_WINDOW_CLOSED` (300026), including for
appointments the job completed. `UNRESOLVED` appointments always receive
that same error. The operation reads
the appointment with `getForUpdate` (`SELECT … FOR UPDATE`) as its first
read, checks status and start time itself, and only then calls the
unconditional `markCompletedByUser` write, so a concurrent cancel, no-show or
job run cannot slip in between. Resolving additional services from the
business service happens while that single-row lock is held.

The business is taken from the stored appointment, never from the request.
The appointment is fetched before the permission check so a `view`-only
employee can be let through when it's their own — see [Managing your own
resource on a `view`
grant](../../object-permissions.md#managing-your-own-resource-on-a-view-grant).

## Price adjustment at checkout

The body is optional (`CompleteAppointmentRequest`, an empty body is the same
as no adjustment). Its `priceAdjustment` (`PriceAdjustmentDraft`) carries:

- `price` — **mandatory**, the final amount charged. It replaces the sum of the
  booked and additional services rather than adding to it. It must be ≥ 0 and
  in the appointment's currency.
- `additionalServiceIds` — optional catalog service ids added at checkout,
  repeated for multiples. Only the ids come from the client: name, price,
  duration and group are resolved from the business service through
  `BusinessClient.getServicesByIds` (`POST
  /api/internal/business/{id}/services`) and stored as snapshots.
- `reason` — optional, at most 2048 characters; blank is stored as absent.

The adjustment is stored only when the appointment ends up `COMPLETED`. It is
also stored on an appointment that staff or the system already completed, replacing any
previous adjustment while still within the edit window. The write runs under
the row lock taken by `getForUpdate`.

```mermaid
flowchart TD
    Start([POST /api/appointments/id/complete]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> HasDraft{body.priceAdjustment present?}
    HasDraft -- Yes --> Negative{price negative?}
    Negative -- Yes --> R422n([422 PRICE_ADJUSTMENT_NEGATIVE_PRICE 300021])
    Negative -- No --> Reason{reason longer than 2048?}
    Reason -- Yes --> R422r([422 PRICE_ADJUSTMENT_REASON_TOO_LONG 300023])
    Reason -- No --> Tx
    HasDraft -- No --> Tx[[Begin transaction]]
    Tx --> Get[AppointmentDataSource.getForUpdate id - row lock]
    Get -- missing --> R404a([404 Error.NotFound])
    Get -- found --> Suspended{AppointmentPermissionDataSource.getPermission appointment.businessId - grant row marked suspended?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller update permission, or view permission and appointment.employee.userId == userId?}
    Perm -- No --> R404b([404 Error.OperationNotAllowed])
    Perm -- Yes --> Status{appointment.status}
    Status -- CANCELLED --> R422a([422 ALREADY_CANCELLED 300005])
    Status -- UNRESOLVED --> R422w
    Status -- SCHEDULED, COMPLETED or NO_SHOW --> Window{appointment dateEnd + 7 days before now?}
    Window -- Yes --> R422w([422 APPOINTMENT_EDIT_WINDOW_CLOSED 300026])
    Window -- No --> Started{already COMPLETED or date_start is not after now?}
    Started -- No --> R422b([422 NOT_STARTED 300018])
    Started -- Yes --> Draft{priceAdjustment present?}
    Draft -- No --> Write
    Draft -- Yes --> Currency{price currency == appointment totalAmount currency?}
    Currency -- No --> R422m([422 PRICE_ADJUSTMENT_CURRENCY_MISMATCH 300022])
    Currency -- Yes --> Extra{additionalServiceIds empty?}
    Extra -- Yes --> Write
    Extra -- No --> Resolve[BusinessClient.getServicesByIds appointment.businessId ids - snapshot as ServiceSnapshot]
    Resolve -- missing or foreign service --> R422s([422 BUSINESS_QUOTE_SERVICE_NOT_FOUND 200013])
    Resolve -- resolved --> Write
    Write{status was SCHEDULED or NO_SHOW?}
    Write -- Yes --> Mark[AppointmentDataSource.markCompletedByUser id - sets COMPLETED and completedBy = USER]
    Write -- No, already COMPLETED --> Adjust
    Mark --> Adjust{adjustment resolved?}
    Adjust -- No --> R200([200 Completed Appointment])
    Adjust -- Yes --> Store[AppointmentDataSource.adjustPrice id adjustment - deletes previous appointment_price_adjustment row, inserts new one with its services]
    Store --> R200a([200 Completed Appointment with priceAdjustment])
```

No event is published.

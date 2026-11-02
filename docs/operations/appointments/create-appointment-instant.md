# Create instant appointment

`POST /api/appointments/instant` → `CreateInstantAppointment`

An employee books an existing client of the business straight into a
schedule, with no request to approve (a walk-in, or a booking taken by
phone). An employee booking themselves is a client booking for themselves
and goes through [Create appointment request](create-appointment-request.md).
The body (`InstantAppointmentDraft`) carries only ids and the quote: the
business, the employee whose schedule it is, the client id, the services with
counts, the date, a note and the quote token from `POST
/api/business/{businessId}/service/quote`. Everything stored comes from the business
service, never from the body.

Business resolves the employee, the client and the services through [Get
staff booking context](../business/get-staff-booking-context.md): the
employee must belong to the business and not be suspended (422
`BUSINESS_EMPLOYEE_SUSPENDED`), and the client must be a client row of that
business (404 `BUSINESS_CLIENT_NOT_EXISTS`). An employee who needs a new
walk-in client creates it first.

Who may book into that schedule is today's appointments permission,
checked with `assertOrSelf(UPDATE, actorId = caller, assigneeId =
employee.userId)` on `appointment_permission_grants`: a caller with the
update grant may book any employee of the business, a caller with only the
view grant only themselves, and anyone else gets 404
(`Error.OperationNotAllowed`). A suspended caller gets 403
`BUSINESS_EMPLOYEE_ACCESS_SUSPENDED`. The check runs in its own short
transaction before the booking transaction, so the booking transaction
still takes its locks before its first plain read.

The quote works as for [Create appointment
request](create-appointment-request.md), through the same
`BookingQuoteVerifier`: the token is reserved once (`reserveOfferToken`,
`SET NX`) before anything else and released if the booking then fails, its
signature, business and service counts must match the draft, and the price
and duration recomputed from business must equal the quoted ones.

The stored client account (`client_user_id`) is the client row's own
account from business, empty for a client without one. The initiator
(`initiator_user_id`) is the employee.

A booking for a client whose account was deleted, or a client the business
deleted, is rejected: it would otherwise store their details after the
erasure ran. `deleted_user` (only when the client has an account) and
`deleted_client` are read with a shared lock as the transaction's first
statements, so a deletion running at the same moment either finishes first
(and the booking is rejected) or waits for the booking to commit and then
erases it.

No event is sent, as before: the client isn't notified of an appointment the
employee booked for them.

```mermaid
flowchart TD
    Start([POST /api/appointments/instant body InstantAppointmentDraft]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Decode{body decodes as InstantAppointmentDraft?}
    Decode -- No, e.g. the Appointment body of older apps --> R400body([400 Bad Request])
    Decode -- Yes --> Reserve{"AppointmentRequestDataSource.reserveOfferToken offerToken - SET NX"}
    Reserve -- already used --> R422t([422 QUOTE_TOKEN_ALREADY_USED 300016])
    Reserve -- reserved --> Quote{"BookingQuoteVerifier.verify - counts positive and distinct, signature valid, business and counts match the draft"}
    Quote -- No --> R400s([400 SERVICES_VALIDATION_FAILED 300014])
    Quote -- Yes --> Resolve[["BusinessClient.getStaffBookingContext businessId employeeId clientId distinct serviceIds"]]
    Resolve -- employee missing --> R404e([404 BUSINESS_EMPLOYEE_NOT_EXISTS 200024])
    Resolve -- employee suspended --> R422s([422 BUSINESS_EMPLOYEE_SUSPENDED 200033])
    Resolve -- client not in the business --> R404c([404 BUSINESS_CLIENT_NOT_EXISTS 200006])
    Resolve -- service missing --> R422n([422 BUSINESS_QUOTE_SERVICE_NOT_FOUND 200013])
    Resolve -- no services --> R422empty([422 BUSINESS_QUOTE_EMPTY_SERVICE_LIST 200014])
    Resolve -- ok --> Perm{"AppointmentPermissionDataSource.getPermission caller businessId - own transaction"}
    Perm -- caller suspended --> R403([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Perm -- "update grant, or view grant and employee.userId == caller" --> Build["Appointment from the context - employee, client with its own account, services expanded per count, SCHEDULED, initiator = caller"]
    Perm -- otherwise --> R404perm([404 Error.OperationNotAllowed])
    Build --> Price{"BookingQuoteVerifier.requireUnchanged total and duration"}
    Price -- price differs --> R400p([400 PRICE_CHANGED 300013])
    Price -- duration differs --> R400d([400 DURATION_CHANGED 300017])
    Price -- same --> Tx[[Begin transaction]]
    Tx --> Deleted["DeletedUserDataSource.isDeletedForShare client.userId - only when the client has an account, LOCK IN SHARE MODE, first statement"]
    Deleted -- deleted --> R404del([404 Error.OperationNotAllowed - the client's account was deleted])
    Deleted -- not deleted --> ClientDeleted["DeletedClientDataSource.isDeletedForShare businessId client.id - LOCK IN SHARE MODE"]
    ClientDeleted -- deleted --> R404client([404 Error.OperationNotAllowed - the business deleted the client])
    ClientDeleted -- not deleted --> Settings[AppointmentSettingsDataSource.getForUpdate businessId]
    Settings -- not found --> R404a([404 Error.NotFound])
    Settings -- found --> PastCheck{appointment.date < now?}
    PastCheck -- Yes --> R422b([422 DATE_IN_PAST 300012])
    PastCheck -- No --> WorkdayCheck{date within business workday?}
    WorkdayCheck -- No --> R422c([422 DATE_NOT_ALLOWED 300003])
    WorkdayCheck -- Yes --> WorktimeCheck{slot within worktime?}
    WorktimeCheck -- No --> R422d([422 TIME_NOT_ALLOWED 300002])
    WorktimeCheck -- Yes --> Overlap{overlaps another appointment?}
    Overlap -- Yes --> R422e([422 APPOINTMENT_EXISTS 300004])
    Overlap -- No --> Store[AppointmentDataSource.create appointment]
    Store --> Visit[eventProducer.send AppointmentEvent.ClientVisitBooked businessId client.id dateEnd]
    Visit --> R200([200 Created Appointment - token stays spent])
    Tx -- any failure after the reservation --> Release["AppointmentRequestDataSource.releaseOfferToken - NonCancellable, best effort"]
```

**Consumed by:** `AppointmentEvent.ClientVisitBooked` → [business: record the client's
visit](../business/on-client-visit-booked.md).

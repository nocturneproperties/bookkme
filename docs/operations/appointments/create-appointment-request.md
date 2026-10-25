# Create appointment request

`POST /api/appointments/request` → `CreateAppointmentRequest`

A client submits an `AppointmentRequestDraft` carrying the signed
`offerToken` produced earlier by the business service's
`POST /api/service/quote` (`IssueServiceQuote`, read-only — not diagrammed
here) alongside only the requested slot and the ids of the employee and
services — no name, contact or pricing data travels from the client, and no
client id either: the caller never gets to assert whose client record a
booking attaches to, it is always the authenticated `userId`. Multiple
instances of the same service are requested explicitly, not by repeating an
id: `draft.services` is a `List<RequestedService>` (`serviceId` + `count`,
e.g. `RequestedService(serviceX, 5)` for 5 counts of service X), one entry
per distinct service — rejected up front with `SERVICES_VALIDATION_FAILED`
if any entry's `count` is not positive, or if the same `serviceId` appears
in more than one entry (a client must fold that into a single entry's
`count`, not send two entries for the same service). Once that shape is
validated, `draft.services` is already the distinct id + count map the rest
of the operation needs — no flattening — and it's compared directly against
the quote's claimed counts (`draft.services.associate { serviceId to count
}` vs. `claimedServiceCounts`). `IssueServiceQuote` still signs the *count
per distinct service id* into `QuoteClaims.CLAIM_SERVICES`
(`QuoteClaims.encodeServiceCounts` — `"<serviceId>:<count>"` entries) and
its own route still accepts a flat, possibly-repeated id list to derive
those counts from (unchanged, out of scope here) — only the draft's wire
shape is count-based. Once the signature and business id checks pass, the
employee, client and *distinct* services are resolved from the business
service in a single call to `GetAppointmentBookingContext`
(`BusinessClient.getAppointmentBookingContext`, given `businessId`,
`employeeId`, the caller's `userId` and the draft's distinct `serviceId`s —
one entry per service, not one per requested instance) — this is the
operation's actual authorization gate for *identity*: an employee that no
longer exists in the business, or a service that has since been removed,
fails here even if the signature check passed. The client side of that
resolution is a get-or-create keyed on `(businessId, userId)`
(`ClientDataSource.getClientByUserId`, falling back to
`ClientDataSource.getOrCreateIntegratedClient` seeded from the caller's
profile via `UserClient.getUserById` on the very first booking with this
business) inside the same call — so a first-time client is provisioned
without appointments ever needing to know or store a client id itself, and
without a second round trip to business. Only once that context is
resolved does the operation expand it back into one `Service` per requested
instance itself, matching each resolved distinct service against its
`RequestedService.count` (`context.services.associateBy { it.id }`, then
`draft.services.flatMap { List(count) { resolvedService } }`) — business
never sees or returns a repeated-by-count list, appointments is the only
side that needs individual instances, to build one `ServiceSnapshot` per
instance. The full `AppointmentRequest` (with real `EmployeeSnapshot`,
`ClientSnapshot`, one `ServiceSnapshot` per instance) is then built and its
total price *and* total duration compared against the token's frozen
values — the quote signs both
(`QuoteClaims.CLAIM_TOTAL`, `CLAIM_DURATION`), so a service whose duration
changes after the quote was issued (even if its price didn't) is caught the
same way a price change is, instead of silently booking a
longer-or-shorter slot than the client agreed to. There is no business-permission check on
top of this, since the caller is the client booking with the business, not
a staff member. If the business has `automaticApproval` on, the request is
approved immediately by delegating into [Create appointment from a pending
request](create-appointment-from-request.md)'s shared verify/persist logic
instead of being stored as pending. The `offerToken` is reserved up front
with one atomic `CacheClient.setIfAbsent` (Redis `SET NX EX`, 10 minutes, the
same as the quote's own TTL), so two concurrent requests carrying the same
token cannot both get past it: the loser gets `TOKEN_ALREADY_USED` and does
not touch the winner's reservation. A cache outage at this step fails the
request rather than booking without replay protection. On success the
reservation is simply kept. On **any** failure after the reservation, every
error node below the reservation, an unexpected error, or the call being
cancelled, the token is released again (`releaseOfferToken`, run
`NonCancellable`, best effort), so a rejected request does not burn the
client's quote. If that release itself fails it is only logged and the
original error is returned; the token then stays unusable until it expires
and the client has to request a new quote.

```mermaid
flowchart TD
    Start([POST /api/appointments/request]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Reserve{requestDataSource.reserveOfferToken offerToken - atomic SET NX EX 10m}
    Reserve -- already reserved --> R422a([422 TOKEN_ALREADY_USED 300016 - reservation left untouched])
    Reserve -- reserved --> CountCheck{every RequestedService.count > 0?}
    CountCheck -- No --> R422i([422 SERVICES_VALIDATION_FAILED 300014])
    CountCheck -- Yes --> DistinctCheck{draft.services has no duplicate serviceId?}
    DistinctCheck -- No --> R422j([422 SERVICES_VALIDATION_FAILED 300014])
    DistinctCheck -- Yes --> Verify[Verify offerToken signature - SERVICE_QUOTE]
    Verify -- invalid signature --> R422b([422 SERVICES_VALIDATION_FAILED 300014])
    Verify -- valid --> ServicesCheck{draft.services per-id counts and businessId match token claims?}
    ServicesCheck -- No --> R400b([400 SERVICES_VALIDATION_FAILED 300014])
    ServicesCheck -- Yes --> Resolve[[BusinessClient.getAppointmentBookingContext businessId employeeId userId distinct serviceIds]]
    Resolve --> EmptyListCheck{serviceIds empty?}
    EmptyListCheck -- Yes --> R422h([422 BUSINESS_QUOTE_EMPTY_SERVICE_LIST 200014 - unreachable here, ServicesCheck already rejects an empty list])
    EmptyListCheck -- No --> EmployeeLookup[EmployeeDataSource.getEmployee businessId employeeId]
    EmployeeLookup -- missing --> R404b([404 BUSINESS_EMPLOYEE_NOT_EXISTS 200024])
    EmployeeLookup -- found --> SuspendedCheck{employee.isSuspended?}
    SuspendedCheck -- Yes --> R422s([422 BUSINESS_EMPLOYEE_SUSPENDED 200033 - see business/set-employee-suspension.md])
    SuspendedCheck -- No --> ClientLookup{ClientDataSource.getClientByUserId businessId userId}
    ClientLookup -- none yet --> ClientCreate[UserClient.getUserById userId then ClientDataSource.getOrCreateIntegratedClient]
    ClientLookup -- found --> ServicesResolve
    ClientCreate --> ServicesResolve[ServiceDataSource.getServicesByIds distinct serviceIds]
    ServicesResolve -- service missing --> R422g([422 BUSINESS_QUOTE_SERVICE_NOT_FOUND 200013])
    ServicesResolve -- resolved --> Expand[Expand each resolved service by its RequestedService.count]
    Expand --> BuildRequest[Build AppointmentRequest from resolved employee/client and expanded services]
    BuildRequest --> PriceCheck{request.totalAmount == token.total?}
    PriceCheck -- No --> R400a([400 PRICE_CHANGED 300013])
    PriceCheck -- Yes --> DurationCheck{request.dateEnd - request.date == token.duration?}
    DurationCheck -- No --> R400c([400 DURATION_CHANGED 300017])
    DurationCheck -- Yes --> Tx[[Begin transaction]]
    Tx --> Settings[AppointmentSettingsDataSource.getForUpdate businessId]
    Settings -- not found --> R404a([404 Error.NotFound])
    Settings -- found --> AutoApproval{settings.automaticApproval?}
    AutoApproval -- Yes --> Delegate[Delegate to CreateAppointment userId request]
    Delegate --> DelegateFlow[[See create-appointment-from-request.md verify and persist flow]]
    DelegateFlow --> Commit
    AutoApproval -- No --> PastCheck{request.date < now?}
    PastCheck -- Yes --> R422c([422 DATE_IN_PAST 300012])
    PastCheck -- No --> WorkdayCheck{date within business workday?}
    WorkdayCheck -- No --> R422d([422 DATE_NOT_ALLOWED 300003])
    WorkdayCheck -- Yes --> WorktimeCheck{slot within worktime?}
    WorktimeCheck -- No --> R422e([422 TIME_NOT_ALLOWED 300002])
    WorktimeCheck -- Yes --> OverlapRequests{overlaps existing request?}
    OverlapRequests -- Yes --> R422f([422 REQUEST_EXISTS 300001])
    OverlapRequests -- No --> OverlapAppointments{overlaps existing appointment?}
    OverlapAppointments -- Yes --> R422f
    OverlapAppointments -- No --> Create[AppointmentRequestDataSource.create request]
    Create --> Snapshot[AppointmentSubscriptionDataSource.getBusinessSnapshot businessId]
    Snapshot -- missing --> R404d([404 Error.NotFound - logged as data inconsistency])
    Snapshot -- found --> Event[eventProducer.send AppointmentEvent.RequestCreated]
    Event --> Commit[[Commit transaction]]
    Commit --> R204([204 No Content - offerToken stays reserved])
```

**Consumed by:** `AppointmentEvent.RequestCreated` → [notifications: notify the
employee](../notifications/on-appointment-request-created.md). The
`automaticApproval` branch delegates into [Create appointment from a
pending request](create-appointment-from-request.md)'s shared logic and
sends `AppointmentEvent.RequestApproved` instead — see [notifications:
notify the client of
approval](../notifications/on-appointment-request-approved.md).

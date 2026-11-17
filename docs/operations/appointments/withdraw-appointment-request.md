# Withdraw appointment request

`POST /api/appointments/request/{id}/withdraw` → `WithdrawAppointmentRequest`

The person who made a booking request takes it back while the business hasn't
answered it yet. The request is marked `CANCELLED`, the same status the hourly
`appointments-deleteOutdatedRequests` job gives a request whose slot has
passed, so it leaves the business's pending list and stops counting toward the
business's limit of pending requests per client (see [Create appointment
request](create-appointment-request.md)). Only the initiator
(`initiator_user_id`) can withdraw; for anyone else, or an unknown id, the
answer is 404, so the route doesn't reveal which ids exist. The request row is
locked first, so a withdrawal and the business's approval or decline can't both
succeed. No event is sent: the business is not notified.

```mermaid
flowchart TD
    Start([POST /api/appointments/request/id/withdraw]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Lock[AppointmentRequestDataSource.getForUpdate id - SELECT FOR UPDATE, first statement]
    Lock -- not found --> R404a([404 Error.NotFound])
    Lock -- found --> Owner{request.initiatorUserId == caller?}
    Owner -- No --> R404b([404 Error.NotFound])
    Owner -- Yes --> Pending{AppointmentRequest.requirePending}
    Pending -- APPROVED --> R422a([422 REQUEST_ALREADY_APPROVED 300008])
    Pending -- DECLINED or CANCELLED --> R422d([422 REQUEST_ALREADY_DECLINED 300007])
    Pending -- PENDING --> Withdraw[AppointmentRequestDataSource.withdraw id - status CANCELLED]
    Withdraw --> R204([204 No Content])
```

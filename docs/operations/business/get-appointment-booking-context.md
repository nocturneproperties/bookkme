# Get appointment booking context (internal)

`POST /api/internal/business/{id}/appointment-booking-context` → `GetAppointmentBookingContext`

Called by appointments when a user books, to resolve the employee, the
services and the user's client record from the business service's own
data. On the user's first booking with the business it attaches them as an
integrated client. A detached client is only a placeholder for a person
until they use the app, so before inserting a new row it looks for
detached clients of the business holding the user's email or phone
(`getClientsByContacts`) and takes one over instead: the row keeps its
`id` (which appointments store as `client_id`) and `description`, gets the
user's `user_id`, and its name, last name, email and phone are replaced by
the profile's (the stored phone is kept when the profile has none). The
row holding the email wins; any other matching detached row is the same
person twice, so it is deleted. One `BusinessEvent.ClientLinked` then names
the kept row, its new owner and the deleted duplicates, so appointments
moves the duplicates' history onto the kept row and records the account on
all of it. Integrated rows of other users are never taken over.

Phone and email are not verified yet, so whoever puts a phone or email in
their profile takes over the matching contact, notes included.

```mermaid
flowchart TD
    Start([POST /api/internal/business/id/appointment-booking-context]) --> Empty{serviceIds empty?}
    Empty -- Yes --> R422e([422 BUSINESS_QUOTE_EMPTY_SERVICE_LIST 200014])
    Empty -- No --> Tx[[Begin transaction]]
    Tx --> Emp[EmployeeDataSource.getEmployee businessId employeeId]
    Emp -- not found --> R404([404 BUSINESS_EMPLOYEE_NOT_EXISTS 200024])
    Emp -- suspended --> R422s([422 BUSINESS_EMPLOYEE_SUSPENDED 200033])
    Emp -- ok --> Own[ClientDataSource.getClientByUserId businessId userId]
    Own -- found --> Services
    Own -- none --> Profile[UserClient.getUserById userId]
    Profile --> Match[ClientDataSource.getClientsByContacts businessId phone email - detached only, email match first]
    Match -- none --> Create[ClientDataSource.getOrCreateIntegratedClient businessId profile]
    Match -- found --> Others{more than one detached match?}
    Others -- Yes --> Merge["ClientDataSource.deleteClient businessId duplicate.id per duplicate"]
    Others -- No --> TakeOver
    Merge --> TakeOver[ClientDataSource.takeOverClient businessId kept.id profile]
    TakeOver -- ok --> Linked["eventProducer.send BusinessEvent.ClientLinked businessId kept.id userId mergedClientIds = duplicate ids"]
    TakeOver -- taken over concurrently --> Fallback[ClientDataSource.getOrCreateIntegratedClient businessId profile]
    Fallback --> Dups{duplicates deleted?}
    Dups -- Yes --> LinkedFallback["eventProducer.send BusinessEvent.ClientLinked businessId client.id userId mergedClientIds - the row the user actually got, never the one the takeover missed"]
    Dups -- No --> Services
    LinkedFallback --> Services
    Linked --> Services
    Create --> Services[ServiceDataSource.getServicesByIds - expanded per requested count]
    Services -- missing --> R422n([422 BUSINESS_QUOTE_SERVICE_NOT_FOUND 200013])
    Services -- ok --> R200([200 AppointmentBookingContext])
```

**Consumed by:** `BusinessEvent.ClientLinked` → [appointments: move the duplicates' history and give the client's appointments the account](../appointments/on-client-linked.md).

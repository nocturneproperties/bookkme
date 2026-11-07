# Get staff booking context (internal)

`POST /api/internal/business/{id}/staff-booking-context` → `GetStaffBookingContext`

Called by appointments when an employee books an existing client of the
business ([Create instant appointment](../appointments/create-appointment-instant.md)),
to resolve the employee, the client and the services from the business
service's own data. Every field of the body (`StaffBookingContextRequest`:
employee id, client id, service ids) is required.

It only reads. The client is the business's own row (`getClientById`,
scoped to the business), so a client of another business, or one that no
longer exists, is 404 `BUSINESS_CLIENT_NOT_EXISTS`. Nothing is created,
taken over or linked, and no event is sent; a user booking for themselves
goes through [Get appointment booking
context](get-appointment-booking-context.md), which can do all of that.

Who may book into the employee's schedule is decided by appointments, from
its own permission grants, not here.

```mermaid
flowchart TD
    Start([POST /api/internal/business/id/staff-booking-context body StaffBookingContextRequest]) --> Empty{serviceIds empty?}
    Empty -- Yes --> R422e([422 BUSINESS_QUOTE_EMPTY_SERVICE_LIST 200014])
    Empty -- No --> Tx[[Begin transaction]]
    Tx --> Emp[EmployeeDataSource.getEmployee businessId employeeId]
    Emp -- not found --> R404e([404 BUSINESS_EMPLOYEE_NOT_EXISTS 200024])
    Emp -- suspended --> R422s([422 BUSINESS_EMPLOYEE_SUSPENDED 200033])
    Emp -- ok --> Client[ClientDataSource.getClientById businessId clientId]
    Client -- not found --> R404c([404 BUSINESS_CLIENT_NOT_EXISTS 200006])
    Client -- found --> Services[ServiceDataSource.getServicesByIds - expanded per requested count]
    Services -- missing or of another business --> R422n([422 BUSINESS_QUOTE_SERVICE_NOT_FOUND 200013])
    Services -- ok --> R200([200 AppointmentBookingContext])
```

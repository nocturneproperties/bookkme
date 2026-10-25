# Set employee suspension

`PUT /api/business/{businessId}/employee/{id}/suspension` → `SetEmployeeSuspension`

Suspends an employee (`suspended = true`) or reinstates a suspended one
(`suspended = false`). The body, `EmployeeSuspensionRequest`, has a single
`suspended` field (proto field 1). Suspension is stored as
`employee.suspended_at` (`Employee.suspendedAt`, stamped with the current
time, cleared on reinstatement). The employee's grants in
`business_permission_grants` are **not** touched: `getPermission` joins
the employee row and throws `403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED
(200034)` for a suspended caller, so every permission-gated operation
rejects them with a code the client can detect, with no change of its own.
`getViewer` (which only fills in `Business.permissions` / `employeeId` /
`clientId`) reports `NONE` permissions and a null `employeeId` instead. Reinstating restores exactly the grants they had. See
[Suspending an employee](../../object-permissions.md#suspending-an-employee).
The business also disappears from the employee's own business list
(`BusinessDataSource.getUserBusinesses` skips suspended employments and
returns a null `dashboardId` if it pointed there) until they are reinstated.
A suspended employee also cannot be booked by clients — see [Get
appointment booking context](#booking) below.

Only the business owner (`BusinessDataSource.isOwner`) can suspend or
reinstate; a non-owner gets 404 like any other permission failure. The
owner's own employee record cannot be suspended. Requesting the state the
employee is already in is a no-op that returns the employee unchanged and
publishes nothing.

The appointments service keeps its own copy of the appointments grant, so
the change is published as the existing
`BusinessEvent.EmployeePermissionsChanged` carrying the employee's stored
permissions plus `suspended = employee.isSuspended`, which appointments
stores so its own routes answer 403 `BUSINESS_EMPLOYEE_ACCESS_SUSPENDED`
too. No dedicated suspension event
or handler exists. The employee's dashboard (`GET dashboard`) also stops
resolving to the business while suspended (404, as with no dashboard set).

```mermaid
flowchart TD
    Start([PUT /api/business/businessId/employee/id/suspension]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> PermCheck{BusinessDataSource.isOwner caller, businessId?}
    PermCheck -- No --> R404a([404 Error.OperationNotAllowed])
    PermCheck -- Yes --> Lookup[EmployeeDataSource.getEmployee businessId, id]
    Lookup --> Found{employee found?}
    Found -- No --> R404b([404 Error.NotFound])
    Found -- Yes --> Owner{BusinessDataSource.isOwner employee.userId, businessId?}
    Owner -- Yes --> R422o([422 BUSINESS_OWNER_SUSPENSION_NOT_ALLOWED 200032])
    Owner -- No --> Same{employee.isSuspended == suspended?}
    Same -- Yes --> R200a([200 Employee unchanged])
    Same -- No --> Set[EmployeeDataSource.setSuspendedAt id, now or null - findByIdAndUpdate row lock]
    Set --> Event[eventProducer.send BusinessEvent.EmployeePermissionsChanged updated.permissions, suspended = updated.isSuspended]
    Event --> R200([200 Employee with updated suspendedAt])
```

<a id="booking"></a>
## Effect on booking

`GetAppointmentBookingContext` (the internal
`POST /api/internal/business/{id}/appointment-booking-context` route that
`CreateAppointmentRequest` calls) rejects a suspended employee with
`422 BUSINESS_EMPLOYEE_SUSPENDED (200033)` right after resolving the
employee, before the client record is looked up or created. The appointments
service passes that error straight through to the client.

**Consumed by:** `BusinessEvent.EmployeePermissionsChanged` → [appointments:
sync employee permissions](../appointments/on-employee-permissions-changed.md).

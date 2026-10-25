# Set employee permissions

`PUT /api/business/{businessId}/employee/{id}/permissions` → `SetEmployeePermissions`

Grants or revokes independent `view`/`update`/`delete` access to one or more
`BusinessResource`s for one employee in a single request. The body,
`EmployeePermissionsRequest`, has one optional `ResourcePermission` field per
resource — `business` (1), `employees` (2), `clients` (3), `services` (4),
`appointments` (5), proto field numbers in brackets — so every key is named in
the OpenAPI schema instead of being a bare enum ordinal inside a map. An
omitted field keeps that resource's current grant; the route turns the
present fields into the `Map<BusinessResource, ResourcePermission>` the
operation takes (`EmployeePermissionsRequest.grants()`). Only the business
owner (`BusinessDataSource.isOwner`) can manage permissions — holding
`EMPLOYEES.update` (even `FULL` on every resource) is not enough, and a
non-owner caller gets 404 like any other permission failure. The business owner's permissions are
immutable — the owner always holds `FULL` on every resource — so targeting
the owner's employee record is rejected before anything else is checked
about the grants. A body with every field omitted (an empty map) is a no-op that returns the
employee unchanged. The `Employee` row itself is unchanged; the response is
the `Employee` with its merged `permissions`, computed in memory as
`employee.permissions.with(grants)` rather than re-read. Grants can be
changed while the employee is [suspended](set-employee-suspension.md): they
are stored, returned and published as usual, with `suspended = true` on
the event, so the appointments copy stays blocked until reinstatement. See [Resource permissions](../../object-permissions.md) for
the full model.

```mermaid
flowchart TD
    Start([PUT /api/business/businessId/employee/id/permissions]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> PermCheck{BusinessDataSource.isOwner caller, businessId?}
    PermCheck -- No --> R404a([404 Error.OperationNotAllowed])
    PermCheck -- Yes --> Lookup[EmployeeDataSource.getEmployee businessId, id]
    Lookup --> Found{employee found?}
    Found -- No --> R404b([404 Error.NotFound])
    Found -- Yes --> Owner{BusinessDataSource.isOwner employee.userId, businessId?}
    Owner -- Yes --> R422o([422 BUSINESS_OWNER_PERMISSIONS_IMMUTABLE 200030])
    Owner -- No --> Empty{grants empty?}
    Empty -- Yes --> R200a([200 Employee unchanged])
    Empty -- No --> Set[BusinessPermissionDataSource.setPermissions employee, grants - one batch upsert keyed by employee_id/user_id/business_id]
    Set --> Merge[employee.copy permissions = employee.permissions.with grants]
    Merge --> Event[eventProducer.send BusinessEvent.EmployeePermissionsChanged updated.permissions, suspended = updated.isSuspended - once per request]
    Event --> R200([200 Employee with updated permissions])
```

**Consumed by:** `BusinessEvent.EmployeePermissionsChanged` → [appointments:
sync employee permissions](../appointments/on-employee-permissions-changed.md).

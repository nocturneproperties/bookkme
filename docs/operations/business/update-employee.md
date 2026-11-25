# Update employee

`PUT /api/business/{businessId}/employee/{id}` → `UpdateEmployee`

A full-record replace covering profile fields, schedule and provided
services in one write. The body is `EmployeeUpdateModel`, not `Employee`:
permissions are not editable here (they change only through [Set employee
permissions](set-employee-permissions.md), which is restricted to the
business owner and publishes `EmployeePermissionsChanged`). Its proto field
numbers match `Employee`'s, so a client still sending a full `Employee`
decodes cleanly and the extra fields are ignored. The target employee is
looked up by `(businessId, id)` before the write, so a caller holding
`EMPLOYEES.update` on one business cannot edit another business's employee
by id. Every check on the body alone (names, phone, email, schedule) runs
before the transaction, so a rejected body never takes a connection and
answers 422 even to a caller without the permission. The first name takes
2 to 512 characters and the last name 0 to 512, so it may be empty or a
single letter, as sign-up allows.

```mermaid
flowchart TD
    Start([PUT /api/business/businessId/employee/id]) --> PathCheck{path businessId/id == body businessId/id?}
    PathCheck -- No --> R400([400 Bad Request])
    PathCheck -- Yes --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> NameCheck{name 2-512 chars and lastName 0-512 chars?}
    NameCheck -- No --> R422a([422 BUSINESS_EMPLOYEE_VALIDATION_ERROR 200021])
    NameCheck -- Yes --> PhoneCheck{phone valid, if present?}
    PhoneCheck -- No --> R422a
    PhoneCheck -- Yes --> EmailCheck{email valid, if present?}
    EmailCheck -- No --> R422a
    EmailCheck -- Yes --> WorkHours{any active day with empty workingTime?}
    WorkHours -- Yes --> R422b([422 BUSINESS_EMPLOYEE_ACTIVE_DAY_WITHOUT_WORK_HOURS 200022])
    WorkHours -- No --> EmptyRange{schedule.hasEmptyWorkHour - any range with from == to?}
    EmptyRange -- Yes --> R422e([422 BUSINESS_EMPLOYEE_EMPTY_WORK_HOUR_RANGE 200043])
    EmptyRange -- No --> DayOffRange{any dayOff.start > end?}
    DayOffRange -- Yes --> R422c([422 BUSINESS_EMPLOYEE_INVALID_DAY_OFF_RANGE 200023])
    DayOffRange -- No --> Tx[[Begin transaction]]
    Tx --> Suspended{BusinessPermissionDataSource.getPermission - caller is a suspended employee of the business?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller EMPLOYEES.update?}
    Perm -- No --> R404a([404 Error.OperationNotAllowed])
    Perm -- Yes --> Lookup[EmployeeDataSource.getEmployee businessId, id]
    Lookup --> Found{employee found in this business?}
    Found -- No --> R404b([404 Error.NotFound])
    Found -- Yes --> Update[EmployeeDataSource.updateEmployee model]
    Update --> R200([200 Updated Employee with its permissions, via EmployeeEntity.grants])
```

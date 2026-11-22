# Create appointment from a pending request

`POST /api/appointments` → `CreateAppointment` (via `appointmentRequestId`)

Converts an already-existing `AppointmentRequest` into a confirmed
`Appointment`. Shares its verification logic (workday/worktime/overlap
checks) with the auto-approval branch of [Create appointment
request](create-appointment-request.md). A `view`-only employee can
convert their own request; see [Managing your own resource on a `view`
grant](../../object-permissions.md#managing-your-own-resource-on-a-view-grant).

The slot must fit both the business schedule (`AppointmentSettings.schedule`)
and the requested employee's schedule as it is now, so the operation asks the
business service for the employee (`BusinessClient.getAppointmentRescheduleContext`
with no services) before it books. That call is made outside any transaction:
a first short transaction reads the request with a plain read and runs the
permission and status checks, so a caller without access never reaches the
business service, and the writing transaction repeats every check after its
locks. A working range whose end is before its start runs past midnight,
and ranges that touch across days join, so a slot may cross midnight.

In the writing transaction the request is read with
`AppointmentRequestDataSource.getForUpdate` (`SELECT … FOR UPDATE`) as the
first statement and must still be `PENDING` (`AppointmentRequest.requirePending()`).
That row lock is what serializes an approval against a concurrent
[decline](decline-appointment-request.md), a second approval, or the
`cancelOutdated` job: the loser waits for the winner to commit and then sees
the new status instead of overwriting it. The request lock is taken before the
settings lock, and both come before the first plain read, the permission
lookup. InnoDB fixes the transaction's snapshot at its first plain read, so a
permission lookup before the settings lock let an approval that waited behind
another booking check overlaps against a snapshot from before that booking
committed, and book the same slot twice.

```mermaid
flowchart TD
    Start([POST /api/appointments body AppointmentRequestId]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Check[[Short transaction]]
    Check --> ReadRequest[AppointmentRequestDataSource.get appointmentRequestId]
    ReadRequest -- not found --> R404a([404 Error.NotFound])
    ReadRequest -- found --> CheckPerm{getPermission request.businessId - suspended / update or own view grant / requirePending}
    CheckPerm -- suspended --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    CheckPerm -- no access --> R404b([404 Error.OperationNotAllowed])
    CheckPerm -- not pending --> R422s([422 REQUEST_ALREADY_APPROVED 300008 / REQUEST_ALREADY_DECLINED 300007])
    CheckPerm -- ok --> Employee[BusinessClient.getAppointmentRescheduleContext businessId request.employee.id no services - outside any transaction]
    Employee -- employee missing --> R404e([404 BUSINESS_EMPLOYEE_NOT_EXISTS 200024])
    Employee -- employee suspended --> R422g([422 BUSINESS_EMPLOYEE_SUSPENDED 200033])
    Employee -- found --> Tx[[Begin writing transaction]]
    Tx --> GetRequest[AppointmentRequestDataSource.getForUpdate appointmentRequestId - row lock]
    GetRequest -- not found --> R404a
    GetRequest -- found --> Settings[AppointmentSettingsDataSource.getForUpdate request.businessId - row lock, before any plain read]
    Settings -- not found --> R404c([404 Error.NotFound])
    Settings -- found --> Suspended{AppointmentPermissionDataSource.getPermission request.businessId - grant row marked suspended?}
    Suspended -- Yes --> R403s
    Suspended -- No --> Perm{caller update permission, or view permission and request.employee.userId == userId?}
    Perm -- No --> R404b
    Perm -- Yes --> Pending{AppointmentRequest.requirePending - request.status}
    Pending -- APPROVED --> R422e([422 REQUEST_ALREADY_APPROVED 300008])
    Pending -- DECLINED or CANCELLED --> R422f([422 REQUEST_ALREADY_DECLINED 300007])
    Pending -- PENDING --> PastCheck{request.date < now?}
    PastCheck -- Yes --> R422a([422 DATE_IN_PAST 300012])
    PastCheck -- No --> BizDay{settings.requireBookable - business works on date, or inside its range running past midnight?}
    BizDay -- No --> R422bd([422 BUSINESS_DATE_NOT_ALLOWED 300003])
    BizDay -- Yes --> BizTime{business ranges cover date to dateEnd, past midnight joined?}
    BizTime -- No --> R422bt([422 BUSINESS_TIME_NOT_ALLOWED 300002])
    BizTime -- Yes --> EmpDay{employee.schedule works on date, or inside its range running past midnight?}
    EmpDay -- No --> R422ed([422 EMPLOYEE_DATE_NOT_ALLOWED 300027])
    EmpDay -- Yes --> EmpTime{employee.schedule ranges cover date to dateEnd?}
    EmpTime -- No --> R422et([422 EMPLOYEE_TIME_NOT_ALLOWED 300028])
    EmpTime -- Yes --> Overlap{overlaps another appointment?}
    Overlap -- Yes --> R422d([422 APPOINTMENT_EXISTS 300004])
    Overlap -- No --> Create["AppointmentDataSource.create from request - the request's note becomes both note and clientNote, each encrypted under the client's key"]
    Create --> Approve[AppointmentRequestDataSource.approve request]
    Approve --> Snapshot[AppointmentSubscriptionDataSource.getBusinessSnapshot businessId]
    Snapshot -- missing --> R404d([404 Error.NotFound - logged as data inconsistency])
    Snapshot -- found --> Visit[eventProducer.send AppointmentEvent.ClientVisitBooked businessId client.id dateEnd]
    Visit --> Event["eventProducer.send AppointmentEvent.RequestApproved - clientUserId = request.client.userId"]
    Event --> R200([200 Created Appointment])
```

**Consumed by:** `AppointmentEvent.RequestApproved` → [notifications: notify
the client](../notifications/on-appointment-request-approved.md);
`AppointmentEvent.ClientVisitBooked` → [business: record the client's
visit](../business/on-client-visit-booked.md).

# Join business

`POST /api/business/employee_invitation/redeem` → `JoinBusiness`

Any authenticated user can join a business by submitting the invite code
shared with them by the business owner — there is no per-user targeting,
whoever holds a `PENDING` code can join. Joining creates the `Employee`
row for the calling user and grants baseline view-only access
(`view = true`, nothing else) on every `BusinessResource` — customizable
afterward by an owner via [Set employee
permissions](set-employee-permissions.md). `JoinBusinessImpl` (not the
datasource — see below) hashes the
submitted plaintext code (SHA-256, `EmployeeInvitationCode.hash`) and
looks it up via `EmployeeInvitationDataSource.getInvitationByCodeHash` —
the datasource only ever sees the hash, never the plaintext, see [Invite
employee](create-employee-invitation.md). `redeemInvitation` also clears
the invitation's `code_hash` column so it can be handed out again to a
future invitee. Because the hash is gone as soon as it's consumed,
resubmitting an already-redeemed code later looks identical to submitting
a code that never existed — `getInvitationByCodeHash` simply won't find
it, so the caller gets 404 rather than the 422 `ALREADY_PROCESSED` error.
That error is still reached for the genuine race: two requests fetching
the same still-`PENDING` invitation before either commits, where the
loser's `redeemInvitation` call matches zero rows. An empty or
whitespace-only code is rejected up front with 422
`BUSINESS_EMPLOYEE_INVITATION_CODE_EMPTY`, before any transaction or
database lookup is started.

The `getEmployeeByUserId` check is not a lock: one user redeeming two
different pending codes of the same business at once passes it in both
requests. The `employee_business_id_user_id_unique` index on
`(business_id, user_id)` stops the second insert, and
`.onConstraintFailure { }` turns it into the same 422
`BUSINESS_EMPLOYEE_EXISTS` the check returns. The losing transaction rolls
back, its invitation stays `PENDING`, and no events are sent.

```mermaid
flowchart TD
    Start([POST /api/business/employee_invitation/redeem]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Blank{code.isBlank?}
    Blank -- Yes --> R422c([422 BUSINESS_EMPLOYEE_INVITATION_CODE_EMPTY 200031])
    Blank -- No --> Tx[[Begin transaction]]
    Tx --> HashCode[EmployeeInvitationCode.hash the submitted code]
    HashCode --> GetInvite[EmployeeInvitationDataSource.getInvitationByCodeHash code_hash]
    GetInvite -- not found or hash already cleared --> R404a([404 Error.NotFound])
    GetInvite -- found --> Status{invitation.status == PENDING?}
    Status -- No --> R422a([422 BUSINESS_EMPLOYEE_INVITATION_ALREADY_PROCESSED 200017])
    Status -- Yes --> AlreadyEmployee{employeeDataSource.getEmployeeByUserId already an employee?}
    AlreadyEmployee -- Yes --> R422b([422 BUSINESS_EMPLOYEE_EXISTS 200018])
    AlreadyEmployee -- No --> Redeem[EmployeeInvitationDataSource.redeemInvitation id, clears code_hash]
    Redeem -- race lost, already processed --> R422a
    Redeem -- ok --> GetUser[UserClient.getUserById requestUserId]
    GetUser -- error --> RErr([Propagate user-service error])
    GetUser -- ok --> GetBiz[BusinessDataSource.getBusinessById businessId]
    GetBiz -- not found --> R404b([404 Error.NotFound])
    GetBiz -- found --> CreateEmployee[EmployeeDataSource.createEmployee from requestUser, permissions = BusinessPermissions.VIEW_ONLY - also upserts the five business_permission_grants rows]
    CreateEmployee -- employee_business_id_user_id_unique violated, concurrent join --> R422b
    CreateEmployee -- ok --> Event[eventProducer.send BusinessEvent.EmployeeInvitationRedeemed]
    Event --> PermEvent[eventProducer.send BusinessEvent.EmployeePermissionsChanged employee.permissions]
    PermEvent --> R200([200 Created Employee])
```

**Consumed by:** `BusinessEvent.EmployeeInvitationRedeemed` → [notifications:
notify the inviter](../notifications/on-employee-invitation-redeemed.md).
`BusinessEvent.EmployeePermissionsChanged` → [appointments: sync employee
permissions](../appointments/on-employee-permissions-changed.md).

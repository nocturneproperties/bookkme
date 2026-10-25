# Invite employee

`POST /api/business/{businessId}/employee_invitation` → `CreateEmployeeInvitation`

Only a caller holding `EMPLOYEES.update` can invite. No employee identity is collected at invite time — the
operation just mints a short, random invite code the owner shares with a
future employee out of band (verbally, a QR code, etc). The employee later
joins the business themselves via [Join business](join-business.md); the
server never sees or stores the employee's email for this flow. On the
rare chance the generated code collides with an existing one, the
operation retries with a freshly generated code before giving up.

The database never stores the plaintext code — only its SHA-256 hex hash
(`EmployeeInvitationTable.codeHash`, column `code_hash`, see [business
service schema](../../database/business.md)). Hashing happens in
`CreateEmployeeInvitationImpl` (`EmployeeInvitationCode.hash`), not the
datasource — `EmployeeInvitationDataSource.createInvitation` only ever
receives and stores the hash, it has no hashing logic of its own. The
plaintext is generated in memory, hashed before the datasource call, and
handed back to the caller in this one response by overwriting the
datasource's echoed-back result with the plaintext the operation still
holds locally; it cannot be recovered afterward, including by the "Get
employee invitations" list endpoint (a read-only `GET` route, out of scope
for these diagrams — it always returns `code = null` since the plaintext
was never persisted) or by a database compromise. The `code_hash` column
is nullable and cleared the moment an invitation leaves
`PENDING` (redeemed, revoked, or expired — see [Join
business](join-business.md), [Revoke employee
invitation](revoke-employee-invitation.md) and the
`expireEmployeeInvitations` job in [Scheduled (recurring)
jobs](../scheduled-jobs.md)), so its unique index only ever has to stay
collision-free across invitations that are still `PENDING`, not every
invitation ever issued.

A business can hold at most `CreateEmployeeInvitation.MAX_PENDING_INVITATIONS`
(20) `PENDING` invitations at once; redeemed, revoked and expired ones do not
count, so revoking an unused code or letting the daily expiry job run frees a
slot. To keep two concurrent requests from both passing the check, the
operation's first statement is a row lock on the business
(`BusinessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE)`,
`SELECT … FOR UPDATE`, which also serves as the existence check), before even
the permission lookup, and only then counts, so creations for the same
business are serialized until the transaction commits. The order matters:
InnoDB fixes a REPEATABLE READ snapshot at the first plain read, so with the
permission lookup first the counts would read a snapshot from before the lock
wait and miss the invitation the previous holder just committed, letting two
concurrent requests both take the last slot (reproduced against MariaDB in
`BusinessRowLockMariaDbTest`).

The pending cap alone does not stop a create → revoke → create loop, since
each revoke frees a slot. A second, rolling quota closes that: a business can
create at most `CreateEmployeeInvitation.MAX_INVITATIONS_PER_DAY` (50)
invitations in any 24-hour window, counted over every invitation created in
that window regardless of status
(`EmployeeInvitationDataSource.countInvitationsCreatedSince`, backed by the
composite `(business_id, createdAt)` index), so revoking returns no quota.
Processed invitations are hard-deleted 30 days after their last update by the
`deleteProcessedEmployeeInvitations` job in [Scheduled (recurring)
jobs](../scheduled-jobs.md), which keeps the table bounded; that retention is
longer than the quota window, so the rows the quota counts are never deleted
early.

```mermaid
flowchart TD
    Start([POST /api/business/businessId/employee_invitation]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> LockBiz[BusinessDataSource.lockBusiness businessId EXCLUSIVE, SELECT FOR UPDATE, first statement of the transaction]
    LockBiz -- not found --> R404b([404 Error.NotFound])
    LockBiz -- locked --> Suspended{BusinessPermissionDataSource.getPermission - caller is a suspended employee of the business?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller EMPLOYEES.update?}
    Perm -- No --> R404a([404 Error.OperationNotAllowed])
    Perm -- Yes --> Count[EmployeeInvitationDataSource.countPendingInvitations businessId]
    Count -- ">= MAX_PENDING_INVITATIONS 20" --> R422([422 PendingInvitationsLimitReached, BUSINESS_EMPLOYEE_PENDING_INVITATIONS_LIMIT_REACHED 200028])
    Count -- below limit --> CountToday[EmployeeInvitationDataSource.countInvitationsCreatedSince businessId, now - 24h, any status]
    CountToday -- ">= MAX_INVITATIONS_PER_DAY 50" --> R422b([422 DailyInvitationsLimitReached, BUSINESS_EMPLOYEE_DAILY_INVITATIONS_LIMIT_REACHED 200029])
    CountToday -- below limit --> GenCode[Generate random 8-char invite code, plaintext kept in memory only]
    GenCode --> HashCode[EmployeeInvitationCode.hash the code]
    HashCode --> CreateInvite[EmployeeInvitationDataSource.createInvitation businessId invitedBy code_hash]
    CreateInvite -- unique constraint violated, hash collision --> GenCode
    CreateInvite -- ok --> R200([200 Created EmployeeInvitation, plaintext code included once])
```

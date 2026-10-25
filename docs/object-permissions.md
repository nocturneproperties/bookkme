# Resource permissions

Every business-scoped operation checks a caller's grant for a specific
**resource** before doing anything else. The grant is a set of three
independent booleans — `view` / `update` / `delete` — defined once in
`library/permissions`
(`library/permissions/src/main/kotlin/library/permissions/ResourcePermission.kt`)
and shared by every microservice. Unlike the old single ranked level this
replaces, each action is granted independently: an employee can hold
`update` on `CLIENTS` without holding `view` on `EMPLOYEES`, or `view` +
`update` without `delete` on the same resource.

```kotlin
enum class PermissionAction { VIEW, UPDATE, DELETE }

data class ResourcePermission(
    val view: Boolean = false,
    val update: Boolean = false,
    val delete: Boolean = false
) {
    fun grants(action: PermissionAction): Boolean
    fun covers(other: ResourcePermission): Boolean   // true if every bit `other` has, this also has
    companion object { val NONE; val FULL }
}
```

## How a check works

An operation calls `.assert(PermissionAction.X)` on the caller's stored
grant for the resource in question:

```kotlin
businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.CLIENTS)
    .assert(PermissionAction.UPDATE)
```

`assert` throws `com.bookk.core.domain.entity.Error.OperationNotAllowed`
unless the stored grant has that specific bit set — a missing grant row
resolves to `ResourcePermission.NONE` (every bit `false`), so it always
fails. There is no ranking between actions: holding `delete` does not imply
`update` or `view` — each is granted explicitly, on purpose, per resource.

### Managing your own resource on a `view` grant

A second extension, `ResourcePermission?.assertOrSelf(action, actorId,
assigneeId)`, lets a caller who only holds `view` pass an `update` check
when the resource being mutated is assigned to them: it passes when the
grant already grants `action`, **or** when the grant has `view = true` and
`actorId == assigneeId`. Appointments uses this for an employee acting on
their own appointment/request — `CreateAppointmentImpl`,
`UpdateAppointmentImpl`, `CancelAppointmentImpl`, and
`DeclineAppointmentRequestImpl` all pass the caller's `userId` as `actorId`
and the appointment/request's `employee.userId` as `assigneeId`
(`CreateAppointmentRequestImpl`, the client-facing booking entry point, has
no permission gate at all — see its KDoc). A caller who holds `update`
directly is unaffected — they can still act on anyone's appointment. A
`view`-only holder acting on an appointment assigned to a different
employee still gets `OperationNotAllowed`.

`OperationNotAllowed` is a generic infrastructure error, not a
`BusinessError`, so `call.respondWith(result)` maps it to **HTTP 404**, not
403 — a permission failure looks identical to the object simply not
existing (see `core/service`'s `respondWith`).

## Resources

The business service defines five resources in
`com.bookk.business.domain.api.business.entity.BusinessResource`:
`BUSINESS`, `EMPLOYEES`, `CLIENTS`, `SERVICES`, `APPOINTMENTS`. A grant is
always scoped to exactly one `(userId, businessId, resource)` triple.
`APPOINTMENTS` is owned and assigned here too (an employee's appointment
access is managed from the business service's employee screen), but it is
enforced by the appointments service against its own local copy — see
[Storage](#storage).

The appointments service has a single implicit resource (its own
"appointments" grant, covering both settings and appointment records) and
so does not need its own `Resource` enum — `AppointmentPermissionDataSource`
is keyed by `(userId, businessId)` alone.

| Resource | Action | Required by |
|---|---|---|
| `BUSINESS` | `update` | Update the business profile (`UpdateBusinessImpl`) |
| `BUSINESS` | full control (`covers(FULL)`) | Enable the appointments module for a business, checked cross-service (`EnableAppointmentsForBusinessImpl`) |
| `EMPLOYEES` | `view` | List employees, each carrying their own grants as `Employee.permissions` (`GetEmployeesImpl`) |
| `EMPLOYEES` | `update` | Update an employee's own record (`UpdateEmployeeImpl`), create/revoke an employee invitation (`CreateEmployeeInvitationImpl`, `RevokeEmployeeInvitationImpl`), grant or revoke another resource's permission for an employee (`SetEmployeePermissionsImpl`) |
| `CLIENTS` | `view` | List clients (`GetClientsImpl`) |
| `CLIENTS` | `update` | Create/update a client (`CreateClientImpl`, `UpdateClientImpl`) |
| `CLIENTS` | `delete` | Delete a client (`DeleteClientImpl`) |
| `SERVICES` | `update` | Create/update a service or service group (`CreateServiceImpl`, `UpdateServiceImpl`, `CreateServiceGroupImpl`) |
| `SERVICES` | `delete` | Delete a service or service group (`DeleteServiceImpl`, `DeleteServiceGroupImpl`) |
| appointments (local) | `view` | Get appointment settings/history/requests, check whether appointments are enabled (`GetSettingsImpl`, `GetAppointmentHistoryImpl`, `GetAppointmentsForDataImpl`, `GetPendingAppointmentRequestsImpl`, `IsAppointmentsEnabledImpl`) |
| appointments (local) | `update` | Edit appointment settings, create/update/cancel/decline **any** employee's appointment (`EditSettingsImpl`, `CreateAppointmentImpl`, `UpdateAppointmentImpl`, `CancelAppointmentImpl`, `DeclineAppointmentRequestImpl`) — an employee with only `view` can still do this for their own, see [above](#managing-your-own-resource-on-a-view-grant) |

## Granting and revoking permissions

There is no fixed role (the old `EMPLOYEE`/`MANAGER` split is gone).
Instead, the business owner — and only the owner — grants or revokes
the `view`/`update`/`delete` bits of one or more resources for one
employee in a single request:

```
PUT /api/business/{businessId}/employee/{id}/permissions
Body: EmployeePermissionsRequest(business?, employees?, clients?, services?, appointments?: ResourcePermission)
```

`SetEmployeePermissionsImpl` requires the caller to be the business owner
(`BusinessDataSource.isOwner`; `EMPLOYEES.update` alone is not enough —
`Error.OperationNotAllowed`, 404), looks up the target employee, and rejects
the request with `BUSINESS_OWNER_PERMISSIONS_IMMUTABLE` (200030) if that
employee is the owner themselves — the owner always keeps `FULL` on every
resource. Resources not listed keep their grants. It responds with
the updated `Employee`. There is no separate read endpoint: every
`Employee` the business service returns carries that employee's current
grants across all five resources as `Employee.permissions`
(`BusinessPermissions`), so the employee list (`EMPLOYEES.view`) is enough
for a management UI to pre-fill toggles. Note the difference from
`Business.permissions`, which holds the **requesting user's** grants on
that business, whereas `Employee.permissions` holds **that employee's**
grants.

| Grant | Where | Result |
|---|---|---|
| Business creator | `CreateBusinessImpl` (via the owner's `Employee.permissions`, written by `createEmployee`) | `ResourcePermission.FULL` on all five resources |
| Employee who joined | `JoinBusinessImpl` (via the new `Employee.permissions`, written by `createEmployee`) | `view = true` (nothing else) on all five resources — customizable afterward via `SetEmployeePermissions` |
| Employee's permissions changed by the business owner | `SetEmployeePermissionsImpl` | Whatever `ResourcePermission` was requested, for each listed resource (one event per request) |

Every row above that changes an **existing** employee's grants (join, or an
explicit set) publishes `BusinessEvent.EmployeePermissionsChanged`
(`employeeUserId`, `businessId`, `permissions: BusinessPermissions` — the
employee's full, current grant set across all five resources, not just the
one that changed), which the appointments service consumes to keep its own
local copy of the `APPOINTMENTS` grant in sync — see
[Storage](#storage).

## Suspending an employee

Suspension is a separate state, not an all-`false` grant set: an employee
with no grants (`BusinessPermissions.NONE`) is still active and bookable.
The owner suspends or reinstates an employee with

```
PUT /api/business/{businessId}/employee/{id}/suspension
Body: EmployeeSuspensionRequest(suspended: Boolean)
```

which sets or clears `employee.suspended_at` (`Employee.suspendedAt`).
The stored grants are left untouched. Enforcement lives in the
permission reads, not in the operations. Both inner-join `employee` on
`business_permission_grants.employee_id`:

- `BusinessPermissionDataSource.getPermission` — the gate every
  permission-checked operation calls before `.assert(...)` — throws
  `library.permissions.EmployeeAccessSuspended` for a suspended caller. It is a
  `BusinessError` with **403** and `BUSINESS_EMPLOYEE_ACCESS_SUSPENDED`
  (200034), unlike an ordinary permission failure (404), so a client can
  tell "you were suspended" apart from "not found / not allowed". The
  error is thrown *after* `dbQuery { }` returns: `dbQuery` maps any
  throwable that is not a core `Error` to `Error.UnknownError` (500), so a
  `BusinessError` thrown inside it would never reach the client. The
  internal `GET /api/internal/business/{id}/permissions/...` route returns
  the same 403, and `BusinessClient.getPermission` decodes it back into a
  `BusinessError`, so appointments' `EnableAppointmentsForBusiness` passes
  it through unchanged.
- `getViewer` / `getViewers` — used only to attach the requesting user's
  standing to a returned `Business` (business by id, dashboard, business
  list), never as a gate — filters `suspended_at IS NULL` and returns
  `BusinessPermissions.NONE` and a null `employeeId` rather than failing
  the read. Suspension only takes away the employee side: the business
  itself stays readable and the caller's `clientId` is still returned.

The appointments service enforces the same thing against its own copy:
`BusinessEvent.EmployeePermissionsChanged` carries a `suspended` flag that
`SyncEmployeePermission` stores on `appointment_permission_grants.suspended`,
and `AppointmentPermissionDataSource.getPermission` throws the same
`EmployeeAccessSuspended` while it is set. The error class and its code
(`library.permissions.PermissionErrorCodes.EMPLOYEE_ACCESS_SUSPENDED`,
aliased as `BusinessErrorCodes.BUSINESS_EMPLOYEE_ACCESS_SUSPENDED`) live in
`library/permissions` so both services throw the identical error. The
dashboard (`BusinessDataSource.getDashboardBusiness`) treats a dashboard
pointing at a suspended employment as unset, so `GET dashboard` returns its
usual 404.

Owner-only operations (`SetEmployeePermissions`, `SetEmployeeSuspension`)
gate on `BusinessDataSource.isOwner`, not on grants, so a suspended caller
still gets their ordinary 404 there. Reinstating restores exactly the
grants they had. `Employee.permissions` always reports the stored grants
(so a management UI can show what reinstatement will restore), and
`Employee.isSuspended` says whether they apply.

Every producer of `BusinessEvent.EmployeePermissionsChanged` publishes the
stored `permissions` together with `suspended = employee.isSuspended`, so the
appointments copy mirrors the real grants at all times — including when the
owner edits a suspended employee's grants — and the flag alone decides
whether they apply. The owner cannot be
suspended (`BUSINESS_OWNER_SUSPENSION_NOT_ALLOWED`, 200032). Client booking is
not permission-gated, so `GetAppointmentBookingContext` checks
`employee.isSuspended` explicitly (`BUSINESS_EMPLOYEE_SUSPENDED`, 200033). A
suspended employee's business is also left out of their own business list
(`BusinessDataSource.getUserBusinesses`, which drops a `dashboardId` pointing
at it too). See
[Set employee suspension](operations/business/set-employee-suspension.md).

## Cross-service checks

A service that needs to check another business's grant without fetching
the whole `Business` entity calls the internal, lean endpoint:

```
GET /api/internal/business/{id}/permissions/{userId}/{resource}
Response: library.permissions.ResourcePermission
```

backed by `GetBusinessPermissionImpl` and exposed to other services through
`BusinessClient.getPermission(userId, businessId, resource)`. This is how
`EnableAppointmentsForBusinessImpl` checks whether the caller has full
control (`covers(ResourcePermission.FULL)`) of `BusinessResource.BUSINESS`
before turning the appointments module on for a business — the
fine-grained equivalent of the old "must be `OWNER`" gate.

## Storage

This is a modular monolith: each microservice persists its own copy of the
grants it needs rather than sharing one table across services.

- **Business service** owns the source-of-truth grants, in
  `business_permission_grants` (`BusinessPermissionDataSourceImpl`), one row
  per `(userId, businessId, resource)`, with `can_view`/`can_update`/
  `can_delete` boolean columns. `getViewers(userId, businessIds)` resolves
  the caller's standing in each requested business as a `BusinessViewer`:
  all five grant rows aggregated into one `BusinessPermissions` (missing
  rows default to `ResourcePermission.NONE`), plus the caller's own
  `employee.id` and integrated `client.id` there, or `null` when they have
  none. It runs three queries (active employees, their grants, clients)
  however many businesses are asked for; `getViewer(userId, businessId)`
  is the single-business form. `Business.viewedBy(viewer)` copies the
  result onto `Business.permissions` / `employeeId` / `clientId` —
  `GetBusinessById`, `GetDashboardBusiness`, and `GetUserBusinesses`
  (`GET /api/business/mine`, one `getViewers` call for the whole list) all
  do this per request rather than storing it on the business row itself.
  A suspended employee gets `employeeId = null` along with `NONE`
  permissions, matching the list and dashboard, which skip that business
  for them. `GetBusinessById` always takes the requesting user; other
  services read a business through `GetBusinessDetails` instead (the
  internal `GET /api/internal/business/{id}` route and
  `BusinessClient.getBusinessById`), which has no caller and leaves all
  three at their row defaults. `CreateBusiness` returns the owner's
  freshly created employee id.
  Every grant row also carries an `employee_id` FK to the `employee` it
  belongs to (`ON DELETE CASCADE`), so `Employee.permissions` is an
  ordinary DAO referrer (`EmployeeEntity.grants`,
  `BusinessPermissionGrantEntity`). The employee list eager-loads it with
  `.with(..., EmployeeEntity::grants)`, which is one grants query for N
  employees. Creating an employee (`EmployeeEntity.new`) upserts that
  employee's five grant rows, and deleting one removes them. `user_id` and
  `business_id` stay on the row so permission checks
  (`getPermission(userId, businessId, resource)`) don't need a join.
- **Appointments service** keeps its own local copy in
  `appointment_permission_grants` (`AppointmentPermissionDataSourceImpl`),
  one row per `(userId, businessId)` with the same three boolean columns —
  a single implicit resource, no `resource` column needed. This is embedded
  in `AppointmentSettings.permissions`, attached by `GetSettingsImpl` and
  `EditSettingsImpl` the same way `Business.permissions` is. The business
  owner's row is written once, when the business enables appointments
  (`EnableAppointmentsForBusinessImpl`, seeded with `ResourcePermission.FULL`
  after the cross-service check above succeeds). An employee's row is
  written/overwritten by `SyncEmployeePermission`, called from
  `AppointmentEventHandler` in reaction to
  `BusinessEvent.EmployeePermissionsChanged` (reading just the `appointments`
  field off the published `BusinessPermissions`), and is a no-op if the
  business hasn't enabled appointments yet
  (`AppointmentSubscriptionDataSource.isBusinessEnabled`) — an employee
  granted or promoted before their business turns appointments on gets no
  row until their permissions change again after that point. See
  [React to an employee permissions
  change](operations/appointments/on-employee-permissions-changed.md) for
  the full flow.

Both stores use the same three-boolean shape, so `ResourcePermission`,
`.assert(...)`, `.assertOrSelf(...)`, and `.covers(...)` behave identically
regardless of which service's table backs the check.

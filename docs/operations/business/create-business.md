# Create business

`POST /api/business` → `CreateBusiness`

A user can own several businesses, up to their own limit (`max_businesses`,
20 unless an operator stored another value). Only the businesses they own
count (`BusinessDataSource.countBusinessesOwnedBy`); the ones they work at
don't. The count runs after their `user_limits` row is locked, so two
creations by one user can't both take the last place. No route deletes a
business, so the way back under the limit is an operator raising it.

The owner is also inserted as an `EmployeeTable` row for the new business,
snapshotting their name/lastName/phone/email from `UserClient.getUserById`
at creation time. This is what makes `GetUserBusinesses` return owned and
employed-at businesses through the same query, and it is also why
`JoinBusiness` rejects the owner redeeming an invite code for their own
business — `EmployeeDataSource.getEmployeeByUserId` already finds their
owner-employee row and `JoinBusiness.Error.EmployeeExist` fires. The owner
starts on the new business's working week (`business.schedule.workingWeek()`,
Monday to Friday 9 to 17 by default), since bookings check the employee's
schedule as well as the business's.

A demo account (the access token's `demo` claim) may own one business
(`UserLimits.DEMO_MAX_BUSINESSES`) instead of its stored limit, so it can't
grow a footprint the demo erasure then has to remove. Its business is stored
with `business.demo` set and gets a link named `demo-` plus 8 random letters
and digits (`BusinessFounder`, a name already in `business_link` is skipped),
so prospects can share it right away while nobody can walk through demo
pages. After the [upgrade](../authorization/demo-upgrade.md) the regular limit
applies again and [the demo flag is cleared](on-demo-upgraded.md). Validation
runs before the transaction.

```mermaid
flowchart TD
    Start([POST /api/business]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> NameCheck{NameValidator.isValid name?}
    NameCheck -- No --> R422a([422 BUSINESS_NAME_VALIDATION_ERROR 200002])
    NameCheck -- Yes --> CurrencyCheck{CurrencyValidator.isValid currencyCode?}
    CurrencyCheck -- No --> R422c([422 BUSINESS_INVALID_CURRENCY 200035])
    CurrencyCheck -- Yes --> Tx[[Begin transaction]]
    Tx --> LockLimits[UserLimitsDataSource.lockForUser userId - first statement, INSERT IGNORE then SELECT FOR UPDATE on user_limits]
    LockLimits --> Owned{BusinessDataSource.countBusinessesOwnedBy userId >= limit - 1 for a demo account, else userLimits.maxBusinesses?}
    Owned -- Yes --> R422m([422 BUSINESS_OWNED_BUSINESSES_LIMIT_REACHED 200045])
    Owned -- No --> ResolveOwner[UserClient.getUserById userId]
    ResolveOwner --> Create[BusinessDataSource.createBusiness userId name currencyCode timeZone demo = isDemo]
    Create --> CreateEmployee[EmployeeDataSource.createEmployee owner as employee of business.id, schedule = business.schedule.workingWeek, permissions = BusinessPermissions.FULL - also upserts the five business_permission_grants rows]
    CreateEmployee --> Demo{demo account?}
    Demo -- No --> R200([200 Created Business - permissions = FULL, employeeId = owner employee id, clientId = null])
    Demo -- Yes --> Link[BusinessLinkDataSource.getLinkByNameForUpdate random demo- name until a free one, up to 5 tries, then createLink]
    Link --> R200
```

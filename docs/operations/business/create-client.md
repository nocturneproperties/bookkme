# Create client

`POST /api/business/{businessId}/clients` → `CreateClient`

Clients come in two flavors — `Detached` (a business-entered contact with
no app account) and `Integrated` (linked to a real user) — routed to
separate datasource inserts based on the sealed `Client` type. Both `phone`
and `email` are optional, but at least one must be present. Uniqueness is
enforced per business+phone, and only checked when a phone is given; email
is only format-validated, not deduplicated.

```mermaid
flowchart TD
    Start([POST /api/business/businessId/clients]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Suspended{BusinessPermissionDataSource.getPermission - caller is a suspended employee of the business?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller CLIENTS.update?}
    Perm -- No --> R404a([404 Error.OperationNotAllowed])
    Perm -- Yes --> ContactCheck{phone or email present?}
    ContactCheck -- No --> R422c([422 BUSINESS_CLIENT_MISSING_CONTACT_INFO 200025])
    ContactCheck -- Yes --> NameCheck{name and lastName valid?}
    NameCheck -- No --> R422a([422 BUSINESS_CLIENT_NAME_VALIDATION_ERROR 200005])
    NameCheck -- Yes --> HasPhone{phone provided?}
    HasPhone -- Yes --> PhoneCheck{PhoneValidator.isValid phone?}
    PhoneCheck -- No --> R422a
    PhoneCheck -- Yes --> Dup{clientDataSource.getClient businessId phone != null?}
    Dup -- Yes --> R422b([422 BUSINESS_CLIENT_EXISTS 200004])
    Dup -- No --> HasEmail{email provided?}
    HasPhone -- No --> HasEmail
    HasEmail -- Yes --> EmailCheck{EmailValidator.isValid email?}
    EmailCheck -- No --> R422a
    EmailCheck -- Yes --> TypeCheck{Client type}
    HasEmail -- No --> TypeCheck
    TypeCheck -- Detached --> CreateDetached[ClientDataSource.createDetachedClient]
    TypeCheck -- Integrated --> CreateIntegrated[ClientDataSource.createIntegratedClient]
    CreateDetached --> R200([200 Created ClientRemote])
    CreateIntegrated --> R200
```

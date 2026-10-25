# Update client

`PUT /api/business/{businessId}/clients/{id}` → `UpdateClient`

Non-null fields on the request are applied, all others are left
unchanged. Personal info (`name`, `lastName`, `phone`, `email`) can only be
changed for a `Detached` client — an `Integrated` client's personal info is
synced from its linked user profile (see `SyncUserProfile`), so any of
those fields present on the request for an `Integrated` client is
rejected; only its `description` can be edited either way.

```mermaid
flowchart TD
    Start([PUT /api/business/businessId/clients/id]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> IdCheck{path id == body id?}
    IdCheck -- No --> R400([400 Invalid request])
    IdCheck -- Yes --> Tx[[Begin transaction]]
    Tx --> Suspended{BusinessPermissionDataSource.getPermission - caller is a suspended employee of the business?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller CLIENTS.update?}
    Perm -- No --> R404a([404 Error.OperationNotAllowed])
    Perm -- Yes --> Fetch[ClientDataSource.getClientById businessId id]
    Fetch --> Found{client found?}
    Found -- No --> R404b([404 UpdateClient.Error.NotFound])
    Found -- Yes --> TypeCheck{client is Integrated and a personal field is present?}
    TypeCheck -- Yes --> R422d([422 BUSINESS_CLIENT_PERSONAL_INFO_NOT_EDITABLE 200026])
    TypeCheck -- No --> NameCheck{name/lastName valid, if present?}
    NameCheck -- No --> R422a([422 BUSINESS_CLIENT_NAME_VALIDATION_ERROR 200005])
    NameCheck -- Yes --> PhoneCheck{phone valid, if present?}
    PhoneCheck -- No --> R422a
    PhoneCheck -- Yes --> Dup{phone present and ClientDataSource.getClient businessId phone belongs to another client?}
    Dup -- Yes --> R422b([422 BUSINESS_CLIENT_EXISTS 200004])
    Dup -- No --> EmailCheck{email valid, if present?}
    EmailCheck -- No --> R422a
    EmailCheck -- Yes --> Update[ClientDataSource.updateClient businessId model]
    Update --> Updated{row updated?}
    Updated -- No --> R404b
    Updated -- Yes --> R200([200 Updated ClientRemote])
```

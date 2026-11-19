# Create client

`POST /api/business/{businessId}/clients` → `CreateClient`

Always creates a `Detached` client, a contact the business manages. The
body is a `ClientRemote`, whose `userId` is ignored: a business can't link
a client to an app account, since that would let it attach any account
with whatever name and contacts it typed. A client becomes `Integrated`
only through that user's own first booking with the business, which takes
the detached row over ([Get appointment booking
context](get-appointment-booking-context.md)). The business service never
looks the phone or email up in the user service either, so creating a
client reveals nothing about which contacts have an app account. Both `phone`
and `email` are optional, but at least one must be present. The email is
unique per business across every client, detached or integrated, enforced
only by the unique index `client_business_id_email_unique`; several clients
may share a phone. The insert that violates the index is mapped
by `.onConstraintFailure` to `BUSINESS_CLIENT_EXISTS`, so there is no
lookup before it. The business's client limit (`max_clients`, 20,000 unless an
operator stored another value) is checked under an exclusive business lock, so
two concurrent creations can't both take the last slot. Clients created by an
app user's own booking are never refused, so they can take a business past the
limit. Many contacts at once go through [Import
clients](import-clients.md).

```mermaid
flowchart TD
    Start([POST /api/business/businessId/clients]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Lock[BusinessDataSource.lockBusiness businessId EXCLUSIVE - first statement, gives LockedBusiness.limits]
    Lock -- not found --> R404nf([404 Error.NotFound])
    Lock -- locked --> Suspended{BusinessPermissionDataSource.getPermission - caller is a suspended employee of the business?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller CLIENTS.update?}
    Perm -- No --> R404a([404 Error.OperationNotAllowed])
    Perm -- Yes --> NameCheck{name and lastName valid?}
    NameCheck -- No --> R422a([422 BUSINESS_CLIENT_NAME_VALIDATION_ERROR 200005])
    NameCheck -- Yes --> FormatCheck{PhoneValidator / EmailValidator accept the given phone and email?}
    FormatCheck -- No --> R422a
    FormatCheck -- Yes --> ContactCheck{phone or email present?}
    ContactCheck -- No --> R422c([422 BUSINESS_CLIENT_MISSING_CONTACT_INFO 200025])
    ContactCheck -- Yes --> Count{ClientDataSource.countClients businessId >= limits.maxClients?}
    Count -- Yes --> R422l([422 BUSINESS_CLIENTS_LIMIT_REACHED 200046])
    Count -- No --> CreateDetached[ClientDataSource.createDetachedClient - body userId ignored]
    CreateDetached -- email unique index violated --> R422b([422 BUSINESS_CLIENT_EXISTS 200004])
    CreateDetached --> R200([200 Created ClientRemote])
```

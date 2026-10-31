# Import clients

`POST /api/business/{businessId}/clients/import` → `ImportClients`

Batch version of [Create client](create-client.md) for importing contacts,
e.g. from the phone book: up to `ImportClients.MAX_CLIENTS` (500) detached
clients per request, so a larger phone book is sent in several requests.
The body carries no `userId`, so an import can never create an integrated
client. Every entry goes through the same field validation as a single
create (`Client.validatedFields()`); instead of failing the request, an
invalid entry is skipped and reported in `rejected` with its position in
the request and the error code a single create would have returned. Valid
entries are created in one batch insert, all in one transaction.

A phone or email is unique per business across every client, detached or
integrated (`client_business_id_phone_unique`,
`client_business_id_email_unique`), so an entry whose phone or email
already belongs to a client of the business, or to an earlier entry of
the same import, is rejected with `BUSINESS_CLIENT_EXISTS`. Emails are
compared ignoring case, like the index's collation. Existing clients are
read with one `getClientsByContacts` query for every phone and email of
the import. A concurrent write that takes one of those values between that
read and the insert fails the insert on the unique index, which rolls the
whole import back as `BUSINESS_CLIENT_EXISTS`; retrying it reports that
entry as rejected.

```mermaid
flowchart TD
    Start([POST /api/business/businessId/clients/import]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Suspended{BusinessPermissionDataSource.getPermission - caller is a suspended employee of the business?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller CLIENTS.update?}
    Perm -- No --> R404a([404 Error.OperationNotAllowed])
    Perm -- Yes --> Size{more than 500 clients?}
    Size -- Yes --> R422s([422 BUSINESS_CLIENT_IMPORT_TOO_LARGE 200039])
    Size -- No --> Validate[validatedFields per entry - invalid entry goes to rejected with 200005 or 200025]
    Validate --> Lookup[ClientDataSource.getClientsByContacts businessId phones emails]
    Lookup --> Dedup[entry whose phone or email is taken by an existing client or an earlier entry goes to rejected with 200004]
    Dedup --> Any{any entry accepted?}
    Any -- No --> R200([200 ClientImportResult created and rejected])
    Any -- Yes --> Insert[ClientDataSource.createDetachedClients businessId accepted]
    Insert -- unique index violated by a concurrent write --> R422e([422 BUSINESS_CLIENT_EXISTS 200004])
    Insert -- ok --> R200
```

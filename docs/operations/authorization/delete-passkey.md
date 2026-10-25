# Delete passkey

`DELETE /api/auth/passkey/{id}` → `DeletePasskey`

The delete is written as a single conditional statement scoped to
`(id, authId)` that also guards against removing an account's only
remaining passkey — the guard and the write share one row lock, so a
concurrent add/delete pair can't race past it. A `0`-row result means
either the passkey wasn't found, didn't belong to this auth record, or was
the last one.

```mermaid
flowchart TD
    Start([DELETE /api/auth/passkey/id]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Delete[PassKeyDataSource.deletePasskey id authId - guarded by count > 1]
    Delete --> RowCount{deletedRowCount == 0?}
    RowCount -- Yes --> R422([422 LAST_PASSKEY 200006 - or not found -> 404])
    RowCount -- No --> R204([204 Passkey deleted])
```

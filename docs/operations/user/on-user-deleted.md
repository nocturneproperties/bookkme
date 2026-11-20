# React to user deletion

Kafka topic `AuthEvent.UserDeleted` → `UserEventHandler` → `DeleteUser`

Produced by [Delete account](../authorization/delete-account.md), the [`auth-handleDormantAccounts` job](../scheduled-jobs.md), the compensation of a failed [Verify sign-up](../authorization/sign-up.md) or [Sign in with Telegram](../authorization/telegram-sign-in.md) and, after a backup restore, the [`auth-reapplyErasures` job](../scheduled-jobs.md). The user
service owns the profile row itself, so this is a hard delete, and the
user's legal document acceptances go with it
(`ON DELETE CASCADE`). The user's [data export](../../data-export.md) row
is deleted in the same transaction (`DataExports.discard`), so a document
being built for them is dropped and never announced — the other
consumers ([business](../business/on-user-deleted.md),
[appointments](../appointments/on-user-deleted.md),
[notifications](../notifications/on-user-deleted.md)) anonymize or clean up
their own denormalized copies independently and in no guaranteed order
relative to this one.

The cached profile is evicted inside the transaction, after the row is
locked by the delete; a profile read waits for that lock and only caches
what it read under it, so the erased profile can't be cached again. When
Valkey can't evict it, the whole deletion rolls back and the event takes the
failure path below, instead of leaving the profile cached for 10 minutes.

```mermaid
flowchart TD
    Start([Consume AuthEvent.UserDeleted]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Tx[[Begin transaction]]
    Tx --> Delete["UserDataSource.deleteUser userId - DELETE the profile, then evict the cached profile; a failed eviction fails the transaction"]
    Delete --> Discard["DataExports.discard userId - DELETE data_export row, SubjectKeys.shred its key_id"]
    Discard --> Shred["SubjectKeys.shred userId - tombstone the user's key, so unrelayed UserEvent.Updated rows are dropped"]
    Shred -- throws --> Dlt([Sent to AuthEvent.UserDeleted_dlt topic, logged - no auto-retry])
    Discard -- throws --> Dlt
    Delete -- throws --> Dlt
    Shred -- ok --> Ack([Marked processed])
```

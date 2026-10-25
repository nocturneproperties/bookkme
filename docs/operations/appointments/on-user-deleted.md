# React to user deletion

Kafka topic `AuthEvent.UserDeleted` → `AppointmentEventHandler` → `DeleteUserAppointmentData`

Produced by [Delete account](../authorization/delete-account.md). Strips
this user's appointment footprint regardless of which role they held
(client or employee) — anonymizes rather than deletes appointment rows
(financial/scheduling history for the other party in the appointment is
kept), but hard-deletes their own requests and permission grants.

```mermaid
flowchart TD
    Start([Consume AuthEvent.UserDeleted]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Anon[AppointmentDataSource.anonymizeForUser userId]
    Anon --> DelReq[AppointmentRequestDataSource.deleteForUser userId]
    DelReq --> DelPerm[AppointmentPermissionDataSource.deleteForUser userId]
    DelPerm -- any step throws --> Dlt([Sent to AuthEvent.UserDeleted_dlt topic, logged - no auto-retry])
    DelPerm -- ok --> Ack([Marked processed])
```

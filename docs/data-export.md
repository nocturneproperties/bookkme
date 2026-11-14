# Data export

A user can download everything Bookk holds about them (GDPR Art. 15 and 20),
and the owner of a business can download everything the business holds
(Art. 28(3)(g)). Both are one JSON document, built in the background,
confirmed with a passkey action confirmation token, and kept 72 hours. Scope and
decisions: issue #139.

## Lifecycle (`library/export`)

The mechanics are shared by every service that offers an export, in
`library/export` (`api` / `impl` / `route`). Each such service has its own
`data_export` table (one row per owner: a user, or a business) and binds
`dataExportModule(Scope)` plus a `DataExportAssembler` that builds its
document.

| State | Meaning | Wire `DataExportStatus` |
|---|---|---|
| `PENDING` | Requested, waiting for the build job | `RUNNING` |
| `RUNNING` | A build holds the row's claim (`claim_id`, `claimed_until`) | `RUNNING` |
| `READY` | `document` holds the encrypted gzipped JSON until `expires_at` | `READY` (`NONE` once expired) |
| `FAILED` | Three builds failed | `FAILED` |

- **Start** (`DataExports.start`, inside the caller's transaction) locks the
  owner's row first (`SELECT … FOR UPDATE`). No row: inserts a `PENDING`
  one. A queued or running export: returns it unchanged. `READY` and
  requested less than a day ago: `DataExports.Error.TooFrequent`
  (429, `DATA_EXPORT_TOO_FREQUENT` 900002). Otherwise (an older export, or a
  `FAILED` one at any time) the row starts over. Two first exports at the
  same moment both take a gap lock on the missing row and deadlock on the
  insert; `ExposedTransactionManager` retries the loser, which then finds
  the row (`DataExportStartMariaDbTest`).
- **Build job** (`BuildDataExports`, `<svc>-buildDataExports`, every 10
  seconds): first fails abandoned builds that used up their 3 attempts, then
  lists up to 20 claimable rows (`PENDING`, or `RUNNING` with an expired
  claim) and, per row, claims it with a conditional `UPDATE` (a fresh
  `claim_id`, a 10-minute lease, `attempts + 1`), so only one instance builds
  it during a `start-first` deploy. The assembler runs outside any
  transaction (it calls other services). The gzipped document is encrypted
  under a new random key (`SubjectKeys.encryptor(keyId)`, `key_id`) and stored
  with `UPDATE … WHERE claim_id = <ours> AND state = RUNNING`, and only when
  that matched does the same transaction call `announceReady` (the
  service's ready event, through the outbox); otherwise it shreds the new key.
  An export deleted while it was being built (an erasure) therefore gets no
  document and no event. A
  failed build is given back: `PENDING` while attempts remain, `FAILED`
  after the third. A build must finish within the 10-minute lease.
- **Cleanup job** (`DeleteExpiredDataExports`, `<svc>-deleteExpiredDataExports`,
  hourly) locks and deletes rows whose `expires_at` passed and shreds their
  documents' keys in the same transaction: `READY` and `FAILED` rows
  both get `finished_at + 72 hours`. Downloads refuse an expired row even
  before the job ran. Restarting an export and `DataExports.discard` shred the
  old document's key the same way. `discardAll(ownerIds)` discards many
  exports in at most three statements whatever their number (a locking
  read, one `DELETE … IN`, and `SubjectKeys.shredAll`, one upsert writing
  every tombstone), so an erasure touching many businesses never loops
  per business.
- **Download** (`DataExports.document` decrypts with `key_id`;
  `respondDataExport`, `library/export/route`) sends the gzip bytes with `Content-Encoding: gzip` when the request accepts
  gzip, and unpacks them otherwise, always as an `application/json`
  attachment with `Cache-Control: no-store`. It needs the requester's
  session, not a one-time link, so a broken download can be retried until
  the document expires. A document whose key is shredded or missing, or a
  row without `key_id` (only a 0.17.0 instance finishing a build during the
  0.18.0 deploy writes one), is never served; a missing key is logged as an
  error.

The document is stored once, in a `LONGBLOB`, so a single `INSERT` is capped
by MariaDB's `max_allowed_packet` (16 MB by default; prod's `tuning.cnf`
doesn't set it), which applies to the gzipped size. A larger export fails
and ends `FAILED` after three attempts; raise `max_allowed_packet` if that
ever happens. Export rows hold copies of personal data, so they stay out of
NocoDB (not in `nocodb_readable_tables`), and since 0.18.0 every document is
encrypted under its own random key in the service's short-retention schema
(`subject_key`, `docs/agents/personal-data.md`), so no plaintext copy reaches
the table or its binlogs. The rows are in the binlogs and nightly dumps for
up to 30 days like any other row, but a document's key is shredded when the
document expires, is replaced or is discarded, and key backups are kept 7
days, so a document can't be read from any backup 7 days after that. The
0.18.0 migrations (user `V6`, business `V18`) delete every older, plaintext
document.

## Personal export

`POST /api/user/export` with `DataExportRequest(confirmationToken)`: the token is
issued by `POST /api/auth/passkey/confirm_action` for `EXPORT_PERSONAL_DATA`, the
nil business id and the caller's own user id as target
(`ActionConfirmationGrant.personalDataExport`). `GET /api/user/export` reports the
state, `GET /api/user/export/download` hands out the file
(`bookk-personal-data.json`). Diagram: [Start personal data
export](operations/user/start-personal-data-export.md).

`PersonalDataExportAssembler` (user service) reads the user's own data in
one transaction, then asks each service for its part over an internal
route, `GET /api/internal/export/<service>/{userId}` (ProtoBuf, reachable
only inside the cluster, like every `/api/internal` route):

| Section | Source | Contents |
|---|---|---|
| `profile`, `legalAcceptances` | user | Profile with its creation time, accepted legal document versions |
| `account` | auth (`ExportAccountData`) | Account creation and last activity time, passkeys (name, created, last used, backed up; never key material), devices (name, signed in, last sign-in) |
| `businesses` | business (`ExportUserBusinessData`) | The business the user owns, their employee rows (suspended ones too) with permissions, working hours and days off, their client rows as each business keeps them (the business's `description` included), the business their dashboard shows |
| `appointments` | appointments (`ExportUserAppointments`) | Every appointment and request where the user is the client, the employee or the one who booked (`roles`), with times, services, prices, status and employee name |
| `notifications` | notifications (`ExportUserNotificationData`) | Notification settings and channels, devices (language, whether push is registered; never the push token), email and Telegram targets |

Art. 15(4): an appointment or request where the user is only the employee
or only the one who booked holds another person's data, so its client
details (name, phone, email), note, cancellation or decline reason and price
adjustment reason are left out; times, services, prices and status stay.
Where the user is the client, it is complete.

The document has `formatVersion` (1), `generatedAt` and `userId` at the top.
Any part that can't be fetched fails the build, which is retried; a
document is never stored with a part missing.

Account deletion deletes the user's export row in the same transaction as
the profile ([React to user deletion](operations/user/on-user-deleted.md)).
When the build is done, the user service sends `UserEvent.DataExportReady(userId, expiresAt)`, and notifications tells the user with an `ACCOUNT` notification ([React to a ready personal data export](operations/notifications/on-data-export-ready.md)).

## Business export

`POST /api/business/{businessId}/export` with `DataExportRequest(confirmationToken)`,
owner only (anyone else gets 404): the token is issued for
`EXPORT_BUSINESS_DATA` with the business id as business and target
(`ActionConfirmationGrant.businessDataExport`). `GET /api/business/{businessId}/export`
reports the state, `GET /api/business/{businessId}/export/download` hands out
`bookk-business-data.json`. The export row is keyed by the business id in
`business.data_export`. Diagram: [Start business data
export](operations/business/start-business-data-export.md).

`BusinessDataExportAssembler` (business service) reads the business's own
data in one transaction and asks appointments for its part over
`GET /api/internal/export/appointments/business/{businessId}`:

| Section | Source | Contents |
|---|---|---|
| `business` | business | Name, description, address and location, currency, time zone, contacts (the filled-in socials), working hours and days off, client data retention period |
| `serviceGroups` | business | Every service group with its services (price, duration, whether available) |
| `employees` | business | Every employee, the owner included (`role` `OWNER` or `EMPLOYEE`): name and contact details, when they joined, suspension, the services they provide, working hours, days off and permissions per resource |
| `pendingInvitations` | business | Invitations still waiting, with the employee who sent them (null when they left) and when; never the code |
| `clients` | business | Every client row with all its fields, `description` included (it may hold special-category data), whether it is linked to an account, and its last activity |
| `appointments` | appointments (`ExportBusinessAppointments`) | The appointment settings, every appointment and every pending request, with client and employee snapshots, times, services, prices, price adjustments, status, notes and reasons |

It holds nothing the business is not the controller of: no user ids of
employees or clients, no account data (passkeys, devices, notification
settings, legal acceptances), and no appointments its employees have
elsewhere. Any erasure of a person the business holds discards its export
(the row and its key, in the erasure's own transaction), so an erased person
never stays readable in a stored document: deleting a client
([Delete client](operations/business/delete-client.md)), the retention job
for every business that lost a client in its run, re-applying a client
erasure after a restore, and account deletion for every business where the
user had a client or employee record. The owner can start a new export at
once, since the row is gone. Deleting the business
(the owner's account deletion) deletes its export in the same transaction
([React to user deletion](operations/business/on-user-deleted.md)). When the
build is done, business sends `BusinessEvent.ExportReady(businessId, ownerUserId, expiresAt)`, and notifications tells the owner ([React to a ready business data export](operations/notifications/on-business-export-ready.md)).


# Release notes

## 0.18.0

Changes since `monolith/0.17.0`. Sign-up verifies the email with a code before the account exists, the email changes only through a code sent to the new address, business clients are matched to accounts by that verified email only, and notifications can now go out by email, and the in-app contact form is gone in favour of support email; older apps can no longer sign up.

### Clients

- `POST /api/auth/passkey/sign_up/challenge` now emails a 6-digit code (10 minutes) in the `Accept-Language` language and returns `SignUpChallengeResponse` with `emailVerification` (6); it can answer 422 `EMAIL_UNDELIVERABLE` (910005) and 429 `EMAIL_CODE_RESEND_TOO_SOON` (910004). Call it again to resend.
- `POST /api/auth/sign_up` requires `verificationId` (6) and `emailCode` (7), else 400, and answers a bad code with 422 `EMAIL_CODE_INVALID` (910001), `EMAIL_CODE_EXPIRED` (910002) or `EMAIL_CODE_ATTEMPTS_EXCEEDED` (910003). The account takes the address the code was sent to; `userInfo.email` is ignored.
- The sign-up passkey challenge now lives 10 minutes, as long as the code.
- New `POST /api/auth/email` (`EmailChangeRequest`) emails a code to a new address, and `POST /api/auth/email/confirm` (`EmailConfirmation`) stores it; errors as above, plus `EMAIL_EXIST` (1) and `INVALID_EMAIL_FORMAT` (2).
- `PATCH /api/user/me` no longer changes the email: a different one returns 422 `EMAIL_CHANGE_REQUIRES_VERIFICATION` (100006). Send the stored email back or leave it null.
- Several clients of a business may now share a phone: creating, updating and importing clients return `BUSINESS_CLIENT_EXISTS` (200004) only for a taken email.
- A user's first booking takes over only the business's client holding their email, never one holding just their phone (that one stays and the user gets a new client), and a profile change merges clients by email only.
- `GET`/`PUT /api/notification/settings` now include the `EMAIL` channel (off by default); turning it on emails every notification the user gets, in the language of the device they signed in to last.
- Account notices (dormancy warnings, data export ready) are always emailed to the account's address, even with the email channel off.
- `POST /api/user/contactus` is removed (404); link users to the support email instead. The data export no longer has `contactMessages`.
- `POST /api/appointments/enabled/{businessId}` answers 404 when the business's owner has deleted their account.
- An appointment's client who deleted their account now shows as "Deleted Client" (was "Deleted User"), like a client the business deleted.
- A business data export is discarded (status `NONE`, a new one can start at once) when the business loses a client or employee to a deletion, retention or account deletion; personal and business exports ready before the upgrade are deleted too.
- Prod nginx limits each IP to 6 code sends at once, then one per 30 s (`sign_up/challenge` and `POST /api/auth/email` together), to 11 code checks, then one per 6 s (`sign_up` and `email/confirm`), and to 21 `GET /api/auth/passkey/sign_in/challenge`, then one per 6 s; beyond that it answers 429 with an HTML body, so treat any 429 as "try again later".

### Services

- Every email now goes to Postmark with open and link tracking off (`TrackOpens=false`, `TrackLinks=None`), whatever the Postmark server or stream settings say.
- New internal route `POST /api/internal/user/{id}/email`, the only writer of a user's email after sign-up. The unused `UserClient.deleteUser` is gone.
- `BusinessEvent.ClientLinked` from a booking now always has empty `mergedClientIds`; only a profile email change still merges.
- `BusinessDTO` (in `BusinessEvent.Updated` and `getBusinessById`) has a new `ownerUserId` (6); appointments now drops the businesses of a deleted account on `AuthEvent.UserDeleted` itself, and `BusinessEvent.Deleted` still covers businesses whose owner it hasn't filled in yet.
- `POST /api/internal/user` now publishes `UserEvent.Updated` for the new user, so notifications learns the sign-up email.
- New `deletion_hold` flag on `authorization.authentication` and `business.client`, set by staff in the database: `auth-handleDormantAccounts` neither warns nor deletes a held account, and `business-deleteInactiveClients` skips a held client.
- `AppointmentEvent.RequestApproved`, `RequestRejected` and `Cancelled` no longer carry `clientName` (2) and `employeeName` (4); `RequestCreated` drops `employeeName` (4) and gains `clientId` (13); `clientUserId` (1) of `RequestCreated`, `RequestApproved` and `RequestRejected` is never null.
- `AppointmentRequest.client.userId` (5) is always set to the client's own account.
- `UserEvent.Updated`, `AppointmentEvent.RequestCreated` and `BusinessEvent.EmployeeInvitationRedeemed` are now stored encrypted under their person's key in `outbox_event` and `exhausted_event`; an event whose person was erased before delivery or replay is dropped.
- Every erasure (account deletion, client deletion and retention, client merges, a dropped business) now also shreds the erased people's keys in each service, so their encrypted events and exports can no longer be read.
- User profiles (name, last name, email, phone) are now stored encrypted under the user's key; email and phone uniqueness ignores case and surrounding spaces.
- Notification email and Telegram targets are now stored encrypted under the user's key; a target whose key is missing counts as no target, so that user gets no email until their next profile update.
- Business clients (name, last name, phone, email, notes) are now stored encrypted under each client's key and employees under their user's key; a client's email stays unique within the business ignoring case and surrounding spaces.
- Appointment and request client details and notes are now stored encrypted under the client's key and employee names under the employee's; searching appointments by client name now matches the query literally (no `%`/`_` wildcards).
- A `UserEvent.Updated` delivered after the account's deletion no longer re-creates the deleted user's notification settings.
- New counter `personal_data_key_missing_total{source}` counts data left unreadable because a person's key is missing (a key lost in a restore, never an erasure), by event topic or table; it should stay at zero. A profile whose key is missing reads with empty fields until the user re-enters them.

### Upgrade

- Legal drafts in `docs/legal/` now include Polish, configurable operator/jurisdiction details, provider transfer terms, Postmark's 45-day message retention and business-lifetime appointment history; complete the linked reviews and publish new BookkMe privacy-policy and terms versions before using the revised texts.
- Notifications migration `V5` offers the email channel on existing settings rows; it cannot fail on existing data.
- User migration `V6` drops `contact_form` and every stored contact message; save any you still need before deploying.
- Business migration `V18` drops the client phone indexes `client_business_id_phone_unique` and `client_phone` and adds `client.deletion_hold`; authorization migration `V5` adds `authentication.deletion_hold`. Neither can fail on existing data.
- User `V6`, business `V18`, notifications `V5` and new appointments `V11` create the empty key schemas `user_short_retention`, `business_short_retention`, `notifications_short_retention` and `appointments_short_retention` (a `subject_key` table each), which the app's database user needs `ALL PRIVILEGES` on, or the first of them fails with `Access denied … to database '<schema>_short_retention'` and leaves a failed Flyway row to delete.
- User `V6` makes the plaintext `profile` columns nullable and adds encrypted and blind-index ones; the new `user-encryptPlaintextProfiles` job (every 10 minutes) then encrypts every existing profile. Before deploying, `SELECT LOWER(TRIM(email)) e FROM user.profile GROUP BY e HAVING COUNT(*) > 1` (and the same for `phone`) must return nothing, or the job fails on that batch.
- Notifications `V5` makes `notification_email_targets.email` and `notification_telegram_targets.telegram_tag` nullable and adds encrypted columns; the new `notifications-encryptPlaintextTargets` job (every 10 minutes) then encrypts the existing targets. Nothing to do.
- Business `V18` makes the plaintext `client` and `employee` name columns nullable and adds encrypted columns and `client.email_index`; the new `business-encryptPlaintextContacts` job (every 10 minutes) then encrypts every existing client and employee. Before deploying, `SELECT business_id, LOWER(TRIM(email)) e FROM business.client WHERE email IS NOT NULL GROUP BY business_id, e HAVING COUNT(*) > 1` must return nothing, or the job fails on that batch.
- Appointments `V11` makes the plaintext name and note columns of `appointment` and `appointment_request` nullable and adds encrypted ones; the new `appointments-encryptPlaintextSnapshots` job (every 10 minutes) then encrypts every existing appointment and request. Nothing to do.
- User `V6` and business `V18` add `data_export.key_id` and delete every export document built before 0.18.0; affected users and owners just start a new export.
- Appointments `V11` adds `business_has_appointments.owner_user_id` (nullable); the new `appointments-fillBusinessOwners` job (hourly, first run at start) fills it from business and drops businesses that no longer exist or whose owner is deleted. Nothing to do; 0.19.0 makes it NOT NULL.
- User `V6`, business `V18`, appointments `V11` and notifications `V5` add `shredded_subject`, a binlogged list of shredded key ids that a restore uses to destroy those keys again; it cannot fail on existing data.
- Every service's migration adds `personal_data_encrypted` to `outbox_event` and `exhausted_event` (notifications: `exhausted_event` only); it cannot fail on existing data.
- Appointments `V11` makes `appointment_request.client_user_id` NOT NULL and fails on a `NULL` there; before deploying, `SELECT COUNT(*) FROM appointments.appointment_request WHERE client_user_id IS NULL` must return 0 (`V10` filled every row).
- Prod: deploy 0.18.0 with the app stopped first (`order: stop-first`) instead of the usual overlap, so no 0.17.0 copy relays or consumes events with encrypted fields; expect a short outage while it starts.
- Set `APPLICATION_POSTMARK_SERVER_TOKEN`, `APPLICATION_EMAIL_FROM` and `APPLICATION_EMAIL_CODE_SECRET` (at least 32 bytes) for auth (the monolith in prod), or every sign-up and email change fails with 500. A standalone notifications service now needs the first two as well, or its event handling fails to start.
- User, business, appointments and notifications (the monolith in prod) now need `APPLICATION_SUBJECT_MASTER_KEY` (base64 of 32 random bytes) and `APPLICATION_BLIND_INDEX_KEY` (base64 of at least 32), or they fail to start; never change or lose the master key, nothing encrypted can be read without it.
- Prod: run `make vault-update` (generates `vault_email_code_secret`, `vault_subject_master_key` and `vault_blind_index_key`; the deploy refuses to run without the last two), then set `vault_postmark_server_token` and `vault_stack_env.EMAIL_DOMAIN_NAME` with `make vault-edit`; the deploy refuses either one unset. The sender is `notifications@<EMAIL_DOMAIN_NAME>`, not the app's domain: verify that domain in Postmark and add its DKIM and Return-Path records in Cloudflare before deploying. The deploy refuses `CHANGE_ME` and `development_token`, which only dev uses: it logs every email, codes included, instead of sending it.
- Prod: after the vault steps, run `make deploy v=0.17.0` from this checkout first, so the database restarts once with `monolith-mariadb-users-init-v4` (the grants, and the backup user's read access to the key schemas) and `monolith-mariadb-tuning-v4` (`binlog_ignore_db`, which keeps the keys out of the 30-day binlog backups); 0.18.0 must not start before that restart. A self-hosted or dev database needs the grants by hand (`deployment/dev/db/init.sql`).
- Prod: after deploying 0.18.0, run `make infra tags=backup` (`backup-script-v3`, a key dump every 5 minutes kept 7 days) and check that `/srv/backups/bookk/keys/` gets a file within 5 minutes, then `make infra tags=monitoring` (`prometheus_alerts_cfg-v9`, new alerts `KeyBackupMissing` and `PersonalDataKeyMissing`).
- Prod restores now take the keys over SSH from the backup node: `backup-restore.sh sql`/`drill` need `--keys-from "$(scripts/backup-keys-source.sh)"` (`make restore-drill` passes it) or `--without-keys`, and `make fetch-backups` no longer copies `keys/`; see Restoring prod in `deployment/ansible/README.md`.
- Prod: deploy nginx (`make infra tags=nginx`) for the new rate limits and upstream keepalive.
- Prod: SSH and the VPN now refuse Ukrainian addresses; run `make infra tags=geoblock` from outside Ukraine, since the run refuses to cut off a connection from there and changes nothing.

## 0.17.0

Changes since `monolith/0.16.1`. A GDPR release (real erasure, data export, legal document acceptance, retention, backups) that also closes instant-booking and passkey holes; older apps can no longer sign up, book instantly, delete clients or delete their account until they update.

### Fixed

- **The monolith couldn't verify service quote tokens.** `/jwks.json` now serves both auth's and business's keys, and quotes have their own audience (`<domain>/service-quote`). Quotes fetched before the deploy fail with 400 `SERVICES_VALIDATION_FAILED` (300014); fetch a new one.
- **An access token without valid account claims got 500.** It now gets 401.
- **Notifications couldn't store a device shared by several accounts, and replaying a device failed.** Devices are keyed by `(auth_id, device_uuid)` (notifications `V4`); updating a device outside the caller's account returns 404.
- **Swagger was missing request bodies and most responses.** Restored by Kotlin 2.4.21. The notification settings update body is now `NotificationSettingsUpdate`, with the same wire format.
- **Account deletion didn't erase the person from appointments.** Their name, phone, email, note and account are now cleared on all their appointments, including ones linked to them later (`BusinessEvent.ClientLinked`, replacing `ClientMerged`), and requests where they are client or employee are deleted. A booking from a deleted account returns 404.
- **Account deletion left the account, its passkeys and devices in auth**, so the person could sign in again. They are now deleted.
- **A deleted user's upcoming appointments stayed booked.** As client they are cancelled ("User deleted their account") and the employee is notified (`AppointmentEvent.CancelledForDeletedClient`); as employee they are cancelled ("Employee deleted their account") and the client is notified (`AppointmentEvent.Cancelled`). Reassigning instead is #137.
- **Anyone could book an instant appointment into any business at any price.** See the new instant booking under **Changed**.
- **Approving a request or rescheduling could take a slot that was just booked.** A reschedule whose appointment changed meanwhile now gets 409 `TRANSACTION_CONFLICT` (900001); retry with fresh data.
- **Account deletion accepted any account's passkey.** It now needs an action confirmation token (see **Changed**). Its documentation now says 204, which it always returned.
- **Deleting a client left them on the business's appointments.** Their upcoming appointments are now cancelled ("Client was removed by the business"), the rest show "Deleted Client", their requests are deleted, and the employee and the client are notified. Clients deleted earlier need **Upgrade** step 9.
- **Clients never got approval, rejection or cancellation notifications.** The events now carry the client's own account as `clientUserId`; a client without an account gets none.
- **Device language changes and every notification failed on each delivery.** Both work again.
- **An event that failed every attempt was retried forever.** It is now parked in `exhausted_event`.
- **The API documentation listed wrong auth error codes.** It now shows the codes the server always sent: `EMAIL_EXIST` 1 to `UNABLE_TO_GENERATE_REGISTRATION_CHALLENGE` 10.
- The new-request email to an employee no longer shows the business address.
- Passkey requests no longer log `Allowed origin is not a valid URL` for each Android origin.

### Added

- **`GET /api/appointments/{id}`** returns one appointment to a caller with the appointments view grant; otherwise 404, or 403 `BUSINESS_EMPLOYEE_ACCESS_SUSPENDED` (200034) for a suspended employee.
- **Passkey action confirmation.** `POST /api/auth/passkey/confirm_action` turns a fresh assertion of the caller's own passkey into a single-use 2-minute `ActionConfirmationToken` for one action (`DELETE_CLIENT`, `EXPORT_PERSONAL_DATA`, `EXPORT_BUSINESS_DATA`, `DELETE_ACCOUNT`), business and target (the nil UUID and the caller's id for personal actions). New error 422 `PASSKEY_NOT_OWNED` (11). See `docs/operations/authorization/action-confirmation.md`.
- **Legal documents.** `GET /api/user/legal` (public) returns the privacy policy and terms of service versions in effect, `GET /api/user/me/legal` the caller's acceptances, and `POST /api/user/me/legal` (`AcceptedLegalDocuments`) accepts them; an unknown or not yet effective version is 422 `LEGAL_DOCUMENT_VERSION_UNKNOWN` (100003). The operator publishes versions (`docs/legal-documents.md`).
- **Personal data export.** `POST /api/user/export` (`DataExportRequest` with an `EXPORT_PERSONAL_DATA` token) starts it, `GET /api/user/export` reports `DataExport`, and `GET /api/user/export/download` returns `bookk-personal-data.json`, kept 72 hours. Errors: 403 `USER_ACTION_CONFIRMATION_INVALID` (100005), 429 `DATA_EXPORT_TOO_FREQUENT` (900002) for a second export within a day. An `ACCOUNT` notification says when it is ready. See `docs/data-export.md`.
- **Business data export, owner only.** The same three routes under `/api/business/{businessId}/export` with an `EXPORT_BUSINESS_DATA` token return `bookk-business-data.json`. Anyone else gets 404; a bad token 403 `BUSINESS_ACTION_CONFIRMATION_INVALID` (200040).
- **Client retention per business.** `Business.clientDataRetentionMonths` (`@ProtoNumber(14)`, default 36), set through `BusinessUpdateModel.clientDataRetentionMonths` (`@ProtoNumber(10)`, `null` keeps it); outside 12 to 120 is 422 `BUSINESS_CLIENT_DATA_RETENTION_OUT_OF_RANGE` (200041). The daily `business-deleteInactiveClients` deletes clients inactive for longer, never one with an upcoming appointment.
- **Accounts unused for 3 years are deleted**, after warnings 60, 30 and 7 days ahead (`auth-handleDormantAccounts`, a new `ACCOUNT` notification the user can't turn off). An owner is kept while another member of their business is active.
- **Expired data is deleted on a schedule:** requests 60 days after their slot ended, deletion tombstones after 60 days, parked events after 30 days.
- **Erasures survive a backup restore.** Account and client deletions are recorded (`authorization.erased_account`, `business.erased_client`, kept 60 days) and run again after a restore by `auth-reapplyErasures` and `business-reapplyErasures`.
- **Parked events are replayed** on every start, or within a minute of `UPDATE <schema>.exhausted_event SET replay_requested = TRUE WHERE original_topic = '<topic>'`. A row that can't be replayed is logged as `Cannot replay a parked event` and must be handled by hand. See `docs/exhausted-events.md`.
- **The `EventsExhausted` alert** (critical) fires while any event is parked, from `events_exhausted{consumer_group, topic}`.
- **Encrypted backups with 30 days of point-in-time recovery** (a nightly dump plus the binary log every 5 minutes, in `/srv/backups/bookk`), with the `BackupDumpMissing` and `BinlogShippingStalled` alerts. See Backups in `deployment/ansible/README.md`.
- **Notifications in Polish.** `Language.PL` (`@ProtoNumber(2)`), picked for an `Accept-Language` starting with `pl`.

### Changed

- **Sign-up requires accepting the legal documents in effect.** `VerifyAccountCreationRequest.acceptedDocuments` (`@ProtoNumber(5)`) is required: 422 `LEGAL_DOCUMENTS_NOT_ACCEPTED` (100004) if a version is missing or outdated, 400 if the field is absent, so older apps can't sign up.
- **Instant booking takes `InstantAppointmentDraft`** (`businessId` 1, `employeeId` 2, `services` 3, `date` 4, `note` 5, `offerToken` 6, `clientId` 7) instead of an `Appointment`, for an existing client of the business and with a quote token. The appointments update grant books any employee, the view grant only oneself. `INSTANT_APPOINTMENT_TO_OTHER` (300015) is removed. New errors: 400 `PRICE_CHANGED` (300013), `DURATION_CHANGED` (300017), `SERVICES_VALIDATION_FAILED` (300014); 422 `QUOTE_TOKEN_ALREADY_USED` (300016), `BUSINESS_EMPLOYEE_SUSPENDED` (200033), `BUSINESS_QUOTE_SERVICE_NOT_FOUND` (200013), `BUSINESS_QUOTE_EMPTY_SERVICE_LIST` (200014); 404 `BUSINESS_EMPLOYEE_NOT_EXISTS` (200024), `BUSINESS_CLIENT_NOT_EXISTS` (200006); 403 `BUSINESS_EMPLOYEE_ACCESS_SUSPENDED` (200034). Older apps get 400.
- **Account deletion takes `DeleteAccountRequest(confirmationToken)`** (`@ProtoNumber(3)`, a `DELETE_ACCOUNT` token) instead of `VerifyDeleteAccRequest`; otherwise 403 `ACTION_CONFIRMATION_INVALID` (12).
- **Deleting a client takes `DeleteClientRequest(confirmationToken)`** (a `DELETE_CLIENT` token); otherwise 403 `BUSINESS_ACTION_CONFIRMATION_INVALID` (200040).
- **Only the owner can delete a client with an account**; an employee with the delete grant gets 404.
- **Appointments are locked 7 days after they end.** Changing them then fails with 422 `APPOINTMENT_EDIT_WINDOW_CLOSED` (300026).
- **Outcomes can be corrected within those 7 days; cancellation is final.** Completed and no-show appointments can be edited, rescheduled, cancelled or switched, and repeating completion or no-show changes nothing. Changing the date or employee needs a future slot and reopens the appointment as `SCHEDULED`. `ALREADY_COMPLETED` (300006) and `MARKED_NO_SHOW` (300019) are no longer returned.
- **Automatic completion runs after the appointment's end day in the business's timezone.**
- **New status `AppointmentStatus.UNRESOLVED`** (`@ProtoNumber(4)`) for appointments still scheduled 7 days after they end.
- **`Appointment.userId` and `AppointmentRequest.userId` are renamed `initiatorUserId`** (same `@ProtoNumber(2)`).
- **`ClientSnapshot.userId` (`@ProtoNumber(5)`) is the client's own account**, resolved by the server; existing appointments, all booked by staff, have none.
- **Account deletion deletes the person's client and employee rows** in business instead of anonymizing them, and sets the nil UUID wherever their appointments or kept requests pointed to them.
- **Device events carry the account**: `AuthEvent.DeviceLanguageUpdated.authId` (5) and `DeviceDeleted.authId` (4). Events without it are parked.
- **`/api/<service>/internal/openapi` is removed**; use Swagger UI at `/api/<service>/internal/swagger`.
- Lettuce is 7.9.0 (was 6.4.0); Gradle dependencies moved to the modules that use them. No behavior change.

### Deployment

- **Release the app update first.** Older apps can't decode `UNRESOLVED` and can't sign up, book instantly, delete a client or delete their account.
- **Don't run old and new tasks side by side.** The old version can't read status `4` or account-scoped devices, appointments `V10` renames a column it reads, and the internal user creation contract changed. Stop the old task before starting the new one, outside busy hours.
- **Migrations:** appointments `V10` (`user_id` renamed `initiator_user_id`, `client_user_id`, indexes, `deleted_user`, `deleted_client`); authorization `V4` (`last_active_at`, dormancy stage, `erased_account`); user `V5` (`legal_document_version`, `legal_document_acceptance`, `data_export`); business `V17` (`client_data_retention_months`, `client.last_activity_at`, `erased_client`, `data_export`); notifications `V3` and `V4` (device uniqueness). Every schema gets `exhausted_event.replay_requested`.
- **`BusinessEvent.ClientMerged` is removed.** Check none is pending: `SELECT COUNT(*) FROM business.outbox_event WHERE topic = 'business.client_merged'` and the same `original_topic` count in `appointments.exhausted_event`.
- **The first start replays every parked event and deletes expired data.** Delete stale notification events first (`SELECT original_topic, createdAt FROM <schema>.exhausted_event ORDER BY createdAt`), and count what goes: requests older than 60 days, parked events older than 30 days. No client or account is deleted for inactivity, since existing rows start counting at the migration.
- **Public traffic goes through Cloudflare.** Restrict 443 to Cloudflare's ranges. nginx takes the client address from `CF-Connecting-IP`, and `make infra tags=nginx` fails when Cloudflare's ranges change: regenerate with `scripts/cloudflare-real-ip.sh` and update the firewall.
- **Certificates are issued through Cloudflare DNS** (vault `CLOUDFLARE_DNS_API_TOKEN`, replacing `HOSTINGER_API_KEY`).
- **nginx reloads instead of restarting** when only `templates/` or `static/` change. The swarm configs `nginx-v47`, `android_well_known-v3`, `ios_well_known-v5` and `acme_sh_hostinger_dns-v1` are unused; the `identity.` host is removed.
- **WireGuard's endpoint is the manager's IP** (`VPN_ENDPOINT`) and every swarm node joins the VPN (`vpn_client`, `vpn_address` per host, replacing `vpn_host_client`). Run `tags=vpn` without `--limit`.
- **New database users and binary log:** `backup` and `nocodb_writer` (insert into `user.legal_document_version` only), `monolith-mariadb-users-init-v3`, `monolith-mariadb-tuning-v3`. Commit the vault after `make vault-update` before tagging, or the deploy fails.
- **NocoDB reads only the tables listed in `users-init.sql.j2`**: nothing in `authorization`, no outbox, parked events, keys, invitations or tombstones.
- **New configs:** `prometheus_alerts_cfg-v8`, `grafana_dashboard_infrastructure-v2`.
- **New environment variables:** the monolith needs `APPLICATION_APPOINTMENTS_SERVICE_HOSTNAME` and `APPLICATION_NOTIFICATIONS_SERVICE_HOSTNAME` (set by the deploy playbook); standalone auth and user need `APPLICATION_BUSINESS_SERVICE_HOSTNAME`, standalone business `APPLICATION_APPOINTMENTS_SERVICE_HOSTNAME`.
- **`make migration-rehearsal`** runs the checkout's migrations on a restored backup and lists changed row counts.
- **Rolling back to 0.16.1** needs Polish devices set to English first (`UPDATE … SET language = 0 WHERE language = 2` on `authorization.auth_device` and `notifications.device`), and status `4` and multi-account device rows handled.
- `deployment/prod/.env.example` is removed; the vault lists every value.
- A data export over 16 MB gzipped ends `FAILED`.

### Upgrade

1. Set up backups (Setting it up under Backups in `deployment/ansible/README.md`): key pair, `make vault-update`, `make infra tags=swarm`, `make deploy v=0.16.1` (one database restart), `make bootstrap tags=backup`, `make infra tags=monitoring`, `make fetch-backups && make restore-drill`. Commit the vault and public key before tagging.
2. Create a Cloudflare API token (Zone Read, DNS Edit), `make vault-update`, set `CLOUDFLARE_DNS_API_TOKEN` with `make vault-edit`.
3. Move DNS to Cloudflare with proxied records and restrict 443 to `https://www.cloudflare.com/ips-v4` and `ips-v6`. Don't add a DNS-only `vpn.` record.
4. `make infra tags=vpn` without `--limit`, then re-import every device's config from `deployment/ansible/vpn-clients/`.
5. `make infra tags=nginx` (restarts nginx once and issues a new certificate).
6. `make fetch-backups && make migration-rehearsal` from the release checkout. It must list exactly six new, empty tables (`appointments.deleted_client`, `appointments.deleted_user`, `authorization.erased_account`, `business.erased_client`, `user.legal_document_acceptance`, `user.legal_document_version`); anything else stops the release.
7. Deploy the monolith without overlap (see **Deployment**).
8. Reconcile parked device events by account (`docs/exhausted-events.md`): check that a parked `DeviceCreated` still exists in authorization, apply old `DeviceDeleted`/`DeviceLanguageUpdated` rows by hand, then delete only the reconciled rows.
9. Erase clients deleted before this release. Check first, as the `monolith` user:
   ```sql
   SELECT a.business_id, a.client_id, COUNT(*) FROM appointments.appointment a
   LEFT JOIN business.client c ON c.id = a.client_id AND c.business_id = a.business_id
   WHERE c.id IS NULL GROUP BY a.business_id, a.client_id;
   ```
   Rows of a client who still exists (a lost `ClientMerged`) need moving by hand. If all are deleted clients, run once (safe to repeat, sends no notifications):
   ```sql
   UPDATE appointments.appointment a
   LEFT JOIN business.client c ON c.id = a.client_id AND c.business_id = a.business_id
   SET a.status = 2, a.cancellation_reason = 'Client was removed by the business', a.updatedAt = NOW(6)
   WHERE c.id IS NULL AND a.status = 0 AND a.date_start > NOW(6);

   UPDATE appointments.appointment a
   LEFT JOIN business.client c ON c.id = a.client_id AND c.business_id = a.business_id
   SET a.client_name = 'Deleted Client', a.client_phone = NULL, a.client_email = NULL, a.client_user_id = NULL
   WHERE c.id IS NULL;

   DELETE FROM appointments.appointment_request
   WHERE NOT EXISTS (
     SELECT 1 FROM business.client c
     WHERE c.id = appointment_request.client_id AND c.business_id = appointment_request.business_id
   );
   ```
10. Restart the database once outside busy hours (`docker --context swarm-manager service update --force monolith_database`) to apply the grants on the legal document tables. In NocoDB, delete the `authorization` source, sync the other four, and add a `user` source as `nocodb_writer` (SSL on, password from `make vault-view`).
11. Once the privacy policy and terms of service are live, insert their versions through the `nocodb_writer` source (`docs/legal-documents.md`). From then on sign-up must accept them, and a version can't be changed after insert, so check the link first.

## 0.16.1

Changes since `monolith/0.16.0`. A patch release: the app survives a database restart. No API changes and no migrations.

### Fixed

- **The app waits for the database instead of crashing at startup.** When MariaDB restarts, its name `monolith.database` doesn't resolve until it is back, because the database service uses `endpoint_mode: dnsrr`. An app that started in that window exited at once with `UnknownHostException`, and Swarm stopped restarting it after three quick failures. Now `createDatabase` retries while the database can't be reached: unknown host, refused, reset or timed-out connection. It waits 1 s, 2 s, 4 s, 8 s, then every 10 s, about 3 minutes in total, and logs `Database unreachable (…), retrying in …` on each attempt. A rejected login or an unverifiable certificate still fails immediately.

### Deployment

- **The app's restart policy no longer gives up during a database outage.** `monolith` now restarts after 10 s and stops only after 5 restarts within 10 minutes (it was 3 within 120 s). Since each start waits up to about 3 minutes for the database, an outage of any length causes at most 3 restarts in 10 minutes, so Swarm keeps trying. An app that crashes right away for another reason (bad configuration, a failed migration) still stops after 5 quick restarts. The other services in the stack keep their policy.

### Upgrade

1. Deploy the monolith. If a failed update left `monolith_monolith` rolled back (`docker service inspect monolith_monolith --format '{{.UpdateStatus.State}}'` shows `rollback_…`), its spec may still be on `operational` only. A forced update or restart can't reach the database from there; the deploy replaces the whole spec.

## 0.16.0

Changes since `monolith/0.15.11`. A minor release: a new route, new rejection rules and a schema change. Existing requests keep working, but some writes that used to succeed are now rejected (see **Changed**), so clients should handle the new errors.

### Added

- **Clients can be imported in bulk.** `POST /api/business/{businessId}/clients/import` creates up to 500 detached clients at once, for example from the phone book; a larger list goes in several requests. Valid entries are created, and the others come back in `rejected` with their position in the request and the error code a single create would return (`BUSINESS_CLIENT_NAME_VALIDATION_ERROR` 200005, `BUSINESS_CLIENT_MISSING_CONTACT_INFO` 200025, `BUSINESS_CLIENT_EXISTS` 200004). A request with more than 500 entries is rejected with `BUSINESS_CLIENT_IMPORT_TOO_LARGE` (200039). The body has no `userId`, so an import only creates detached clients.

### Changed

- **A phone or email belongs to at most one client per business.** Before, only the phone was checked, and only by a lookup, so two requests at the same time could both pass. Unique indexes now cover phone and email, across detached and integrated clients. Creating or updating a client with an email another client of the business already has is now rejected with `BUSINESS_CLIENT_EXISTS` (200004), whose message is now "Client with this phone or email already exists". Emails compare ignoring case.
- **A business contact becomes the app user's client record.** A detached client is a placeholder until that person uses the app. On a user's first booking with a business, the detached client holding their email, or else their phone, is taken over: it keeps its id, its booking history and the business's notes, and gets the user's account and profile details. Any other detached client holding their phone or email is the same person twice: it is deleted and its appointments and requests move to the kept client (new event `BusinessEvent.ClientMerged`). A profile change to a phone or email that a detached client of the business already has is merged the same way. Phones and emails are not verified yet, so whoever puts a contact's phone or email in their profile takes that contact over.
- **A business can only create detached clients.** `POST /api/business/{businessId}/clients` ignores `userId` in the body; before, a business could link a client to any account it named, with whatever name and contacts it typed. A client is linked to an app user only by that user's own first booking with the business. Creating a client never looks the phone or email up among app users, so it reveals nothing about who has an account.
- **A phone belongs to at most one user.** Updating the profile with a phone or email another account already uses is rejected with the new `USER_CONTACT_TAKEN` (100002); a duplicate email used to fail with a 500. A blank phone now clears it.

### Deployment

- **The database and the cache have their own network.** MariaDB and Valkey moved from `operational` to a new encrypted overlay, `storage`, which only the app, the two exporters and NocoDB join. Before, every container on `operational` could reach them, including nginx and the VPN stack, and needed only a password.
- **The app connects to MariaDB over verified TLS.** Flyway and the connection pool connect with `sslMode=verify-full`. MariaDB 11.8 generates its own certificate, and the driver verifies it against the login password, so there are no certificates to manage. MariaDB now refuses unencrypted connections (`require_secure_transport`, config `monolith-mariadb-encryption-v2`), and `database-exporter` connects with `--tls.insecure-skip-verify`.

- **Migrations.** Business `V16` turns blank client phones and emails into `NULL` and adds the unique indexes `client_business_id_phone_unique` and `client_business_id_email_unique`. User `V4` turns blank phones into `NULL`, trims the rest and adds `profile_phone_unique`. Both run when the monolith starts and fail on existing duplicates, which stops the deploy.
- **Dev only:** IntelliJ no longer flags every opt-in after a Gradle sync. The build derived the opt-ins from built jars, which sync never produces, so the IDE imported none; it now reads them from the resolved dependency graph. Gradle builds are unchanged.

### Upgrade

1. Before deploying, check for duplicates that would stop the migrations. Each query must return no rows:

   ```sql
   SELECT business_id, phone, COUNT(*) FROM business.client WHERE TRIM(phone) <> '' GROUP BY business_id, phone HAVING COUNT(*) > 1;
   SELECT business_id, email, COUNT(*) FROM business.client WHERE TRIM(email) <> '' GROUP BY business_id, email HAVING COUNT(*) > 1;
   SELECT TRIM(phone), COUNT(*) FROM user.profile WHERE TRIM(phone) <> '' GROUP BY TRIM(phone) HAVING COUNT(*) > 1;
   ```

2. `make infra tags=swarm` creates the `storage` network. The monolith deploy fails until it exists.
3. Deploy the monolith. The new config restarts the database once, so expect a few seconds without the database. The migrations run as it starts.
4. `make infra tags=admin` moves NocoDB onto `storage`.
5. In NocoDB, turn on SSL for each MariaDB source. Until then the admin panel can't read the database, since plaintext connections are refused.

## 0.15.11

Changes since `monolith/0.15.10`. A patch release: there are no API, wire-format or schema changes, so clients need no update.

### Fixes

- **`ContainerHighMemoryUsage` no longer fires on page cache.** The alert divided `container_memory_usage_bytes` by the container's memory limit. That metric includes file cache, which the kernel reclaims before it kills a container, so services that read a lot of files (MariaDB, Loki, Prometheus) looked close to their limit while they were safe. The alert now uses `container_memory_working_set_bytes`, usage minus inactive file cache, the same figure `docker stats` shows. The rules config is now `prometheus_alerts_cfg-v6`.

### Changed

- **Every scheduled job name starts with its service's name.** The monolith runs all services' jobs in one scheduler, and log lines only named the job. Auth's key rotation, for example, was plain `rotateSigningKeys`, next to `business-rotateSigningKeys`. The jobs are now:

  | Service | Jobs |
  |---|---|
  | auth | `auth-rotateSigningKeys`, `auth-deleteInactiveDevices` |
  | business | `business-rotateSigningKeys`, `business-deleteDayOffsInThePast`, `business-expireEmployeeInvitations`, `business-deleteProcessedEmployeeInvitations` |
  | appointments | `appointments-markAppointmentsAsCompleted`, `appointments-deleteOutdatedRequests` |

  Job names appear only in logs (`Scheduled job '<name>' failed`), so the jobs run as before. A saved Loki query that matches an old name needs the new one.

### Deployment

- **Released issues are marked on the project board.** After a successful `Publish monolith` run (published and deployed), the new `Mark Released Issues` workflow moves this repository's issues from **Done** to **Released** on `bookkme-board` and comments the release tag on each one. A rollback through `Deploy monolith` doesn't trigger it. A missed run can be started by hand with the tag. It uses the `PROJECT_TOKEN` secret.
- **Dev only:** `rollout.sh` and `rollout-monolith.sh` now build images for the local Docker daemon's architecture (`-Djib.from.platforms=linux/<arch>`). Before, Jib always built `linux/amd64`, which ran under emulation on Apple Silicon. Both scripts also switch to the `default` Docker context before building, not after, so an image is never built and loaded on the prod manager while the `swarm-manager` context is active. The CI publish workflows still build `linux/amd64`.

### Upgrade

1. `make infra tags=monitoring` deploys the corrected alert. Deploys never run the infra playbook, so the monitoring stack only changes when you run it by hand.
2. Merge to `main` before pushing the tag. `workflow_run` fires only from the workflow file on the default branch, so `Mark Released Issues` won't run for this release otherwise.
3. Push the tag `monolith/0.15.11`. On its first run the workflow moves every issue currently in **Done** to **Released** (43 at the time of writing), including issues shipped in earlier releases. To keep the comment accurate, move those to **Released** by hand first.

## 0.15.10

Changes since `monolith/0.15.9`.

### Fixes

- **Passkeys from the re-signed Android app work again.** The Android app now ships with new signing certificates. Passkey registration and sign-in check the app's certificate against the relying party's `android:apk-key-hash:` origins (`RelyingPartyFactory.kt`), and those still listed the old certificates. They now list the new ones, and the old hashes are gone: an app build signed with the old certificates can no longer register or use a passkey.

### Deployment

- **Digital Asset Links updated.** `android_well_known.json` lists the new certificate fingerprints for `me.bookk.android` and `me.bookk.android.dev`. The `me.bookk.android.stage` entry is removed. The swarm config is now `android_well_known-v3`.
- **cAdvisor moved to `ghcr.io/google/cadvisor:v0.60.4`** (was `gcr.io/cadvisor/cadvisor:v0.52.0`). The nodes' Docker 29 uses the containerd image store. On it the old cAdvisor exported no per-container labels, which left every container panel and alert empty.
- **Dev only:** nginx config now depends on the mode. `rollout.sh` uses `nginx.conf` (one upstream per microservice). `rollout-monolith.sh` uses the new `nginx-monolith.conf`, which routes all of `/api/` and the JWKS to `monolith.service`. To bring the environment up by hand for the monolith, set `NGINX_CONF=nginx-monolith.conf`.

## 0.15.9

A patch release that fixes the database running out of connections while the app is idle, and gives NocoDB and Dozzle working logins. There are no API, wire-format or schema changes, so clients need no update.

### Fixed

- **MariaDB no longer runs out of connections when the app is idle.** The app reached MariaDB through a swarm virtual IP, which silently forgets a TCP connection after 15 minutes without traffic. Idle pool connections broke on the app's side while MariaDB kept each one open for 8 hours (`wait_timeout`), until `max_connections` (151) was full. Every pool then failed with `1040 Too many connections` (`Connection is not available … total=0`), preceded by `Failed to validate connection … closed connection` warnings. Three changes, each enough on its own:
  - the connection pools ping idle connections every 5 minutes (HikariCP `keepaliveTime`);
  - the `database` service runs with `endpoint_mode: dnsrr`, so `monolith.database` resolves straight to the container;
  - MariaDB closes a connection idle for 10 minutes (`wait_timeout = 600`, config `monolith-mariadb-tuning-v2`).

  An idle monolith holds 50 connections (5 schemas × pool size 10), so the Grafana **Connections** panel should now sit at about 33%.
- **Dozzle login no longer loops.** Dozzle runs one task per node behind a virtual IP. Each task generated its own session key, so a login made on one task was rejected by the next. All tasks now share `vault_dozzle_session_secret`, and the image is pinned to `amir20/dozzle:v11.3.1` instead of `latest`.

### Added

- **Read-only NocoDB access to the database.** MariaDB gets a `nocodb_reader` user with `SELECT` on the five schemas only, limited to 20 connections. NocoDB's super admin is created from `NOCODB_ADMIN_EMAIL`/`NOCODB_ADMIN_PASSWORD`, telemetry is off, and `NC_ALLOW_LOCAL_EXTERNAL_DBS=true` lets NocoDB add the database at `monolith.database:3306`, an address on the private overlay network. Add one source per schema in the NocoDB UI, logging in as `nocodb_reader`.

### Changed

- The MariaDB `--init-file` secret is renamed from `monolith-mariadb-exporter-init-v1` to `monolith-mariadb-users-init-v1` (template `users-init.sql.j2`). It still creates the `exporter` user, now together with `nocodb_reader`.

### Upgrade

1. `make vault-update` generates `vault_monolith_db_nocodb_password`, `vault_dozzle_session_secret` and `NOCODB_ADMIN_PASSWORD`. Then `make vault-edit` and set `NOCODB_ADMIN_EMAIL`.
2. `make infra tags=orchestrator,admin` redeploys Dozzle and NocoDB. Deploys never run the infra playbook, so these two stacks only change when you run it by hand.
3. Push the tag `monolith/0.15.9`.
   - MariaDB restarts once: its init secret, tuning config and endpoint mode all change. Expect a few seconds without a database.
   - If `docker stack deploy` refuses to change `database` from the virtual IP to `dnsrr`, run `docker service rm monolith_database` and deploy again. The data lives on the `database_volume` volume and is kept.
4. If prod is already failing with `Too many connections` before the deploy, run `docker service update --force monolith_database`. Restarting MariaDB drops the abandoned connections, and the pools recover by themselves.

## 0.15.8 — first public release

The first public release of the Bookk backend: an appointment-booking platform for small businesses. Clients find a business, request or book a time slot and get notified. Owners and their staff manage services, employees, clients, working hours and bookings.

This release ships as a single **monolith** image, `ghcr.io/nocturneproperties/bookkme-server:0.15.7`. The five services it is made of (authorization, user, business, appointments, notifications) are also published as standalone images for split deployments.

### Accounts and sign-in

- Passwordless accounts: sign up and sign in with **passkeys** (WebAuthn). No passwords are stored.
- Several passkeys per account: list, add and delete them.
- Short-lived JWT access tokens with refresh tokens, issued per device session. Signing out ends the device's session.
- Signing keys rotate automatically every day. A retired key stays valid for 7 days, so tokens already issued keep working.
- Devices inactive for 30 days are removed automatically.
- Account deletion. Deleting an account also removes the user's profile, business data, appointments and notification targets in every service.

### User profile

- View and edit your profile (name, contact details).
- Send a contact form to the Bookk team.

### Businesses

- Create a business and edit its details, currency, working schedule and days off. Past days off are cleaned up automatically.
- Pick which business your dashboard shows when you belong to more than one.
- Every price in a business uses the business currency. Service prices are converted to it, and the currency can no longer be changed once the business has services.

### Services

- Services with price and duration, organised into service groups.
- Signed **price quotes** for a selection of services. A quote token is used once, at booking time, so the client pays the price they were shown.

### Employees and permissions

- Invite employees with a single-use **invite code**. Codes expire after 7 days and can be revoked.
- Fine-grained permissions per resource (business, employees, clients, services, appointments), with view, update and delete granted separately.
- An employee can manage their own appointments with view-only access to appointments.
- Suspend and reinstate an employee: a suspended employee keeps their account but loses all access to the business until reinstated.

### Clients

- Client list per business: create, edit and delete clients.
- Clients who use the app are linked to their Bookk account, so they see every business they book with.

### Appointments

- Turn on appointments for a business and configure them: time zone, weekly schedule, break between appointments, a note shown to clients, automatic approval and automatic completion.
- **Booking requests**: clients request a slot, and the business approves or declines it. With automatic approval, the request becomes an appointment right away. Clients can cancel their own pending requests, and requests whose slot has passed are cancelled automatically.
- **Instant appointments**: staff book directly, without a request.
- Reschedule, cancel, complete or mark an appointment as a no-show. With automatic completion on, appointments are marked completed once they end.
- Double booking is prevented: a client can't hold two overlapping bookings at the same business. A slot freed by a cancelled appointment or a declined request can be requested again.
- Daily appointment view and appointment history.
- Prices, employee names and client details on a booking are looked up on the server when the booking is made, never taken from the client app.

### Notifications

- Push notifications (Firebase) for new booking requests, approvals, declines, cancellations and new employees joining a business.
- Per-user notification settings and per-device push tokens.
- Notifications are sent in each device's language. English and Ukrainian are included.

### API

- Every request and response body is ProtoBuf (`application/x-protobuf`).
- The OpenAPI specification is generated from the route definitions.
- Errors carry a stable numeric code and a message. Codes are grouped in blocks per service: auth `0…`, user `100000…`, business `200000…`, appointments `300000…`, notifications `400000…`, shared `900000…`.
- A request for a resource the caller has no access to returns **404**, the same as a resource that doesn't exist, so the API never confirms that a resource exists. A suspended employee gets **403** (`BUSINESS_EMPLOYEE_ACCESS_SUSPENDED`).
- Writes accept an `Idempotency-Key` header, so a client can retry them safely. A retry that arrives while the first attempt is still running gets **409** instead of running a second time.
- A write that loses a concurrency conflict is retried on the server. If the conflict persists, the response is **409** (`TRANSACTION_CONFLICT`), and the client can retry the request.

### Reliability

- Cross-service events are delivered at least once and handled once per consumer: duplicate deliveries are detected and skipped. Every service that sends events does so through a transactional outbox, so an event is sent only when its change has been saved, and never for a change that was rolled back.
- Failed event handlers are retried through a per-service dead-letter topic.

### Deployment and operations

- Production runs the monolith on Docker Swarm with one MariaDB (one schema per service) and one Valkey cache. Events stay inside the process, so production needs no Kafka.
- Provisioning and deployment are scripted with Ansible (`deployment/ansible/`). Pushing a `monolith/<version>` tag builds, publishes and deploys the image. Earlier versions can be redeployed with the same playbook to roll back.
- Database schema changes are applied by Flyway migrations at startup.
- Monitoring: Prometheus, Grafana dashboards, alerting, and centralised logs (Alloy → Loki). The database and the cache are monitored as well.
- Admin access to infrastructure goes through a WireGuard VPN.

### License

Bookk is source-available under the **Functional Source License 1.1, ALv2 Future License** (`FSL-1.1-ALv2`). An [additional permission](LICENSE-SELF-HOSTING.md) lets you self-host Bookk to take bookings for your own business. Offering it to other businesses is not allowed. Each version becomes available under the Apache License 2.0 two years after its release. See [`LICENSE`](LICENSE) and the [README](README.md#license) for details.

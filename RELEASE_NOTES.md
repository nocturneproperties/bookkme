# Release notes

## 0.17.0

Changes since `monolith/0.16.1`. Deleting an account now really removes the person's details from appointments, notifications handle their events again, an event that keeps failing is parked instead of retried forever, a parked event raises an alert and is replayed on the next start or on request, deleting a client erases them from the business's appointments and must be confirmed with a passkey, account deletion now checks that the passkey is the caller's own, instant booking is limited to an employee booking their own schedule, public traffic now goes through Cloudflare, the database is backed up, encrypted, with 30 days of point-in-time recovery, each business sets how long it keeps a client who stops visiting (36 months by default) before the client is deleted, and the server records which version of the privacy policy and terms of service each user accepted. Five new internal events (`BusinessEvent.ClientLinked`, which replaces `BusinessEvent.ClientMerged`, `BusinessEvent.ClientDeleted`, `AppointmentEvent.CancelledForDeletedClient`, `AppointmentEvent.ClientVisitBooked` and `AuthEvent.AccountDormancyWarning`), one migration per schema: appointments `V10` renames a column, adds an indexed one, the `deleted_user` and `deleted_client` tables, and `exhausted_event.replay_requested`, appointments `V11` indexes `employee_user_id`, and the other schemas each add `exhausted_event.replay_requested` (authorization `V4`, which also adds the account's last activity and dormancy stage, user `V5`, business `V17`, notifications `V3`; user `V6` adds the `legal_document_version` and `legal_document_acceptance` tables, and business `V17` also adds `business.client_data_retention_months` and `client.last_activity_at`). The app sees one rename, two new fields, a new route and new rules for deleting clients, three routes for legal document acceptance and a required field on sign-up, without which older apps can no longer sign up, and it must send a passkey step-up token to delete a client (see **Changed**). The infrastructure needs backups set up first (one database restart), a new secret and an `infra` run before the deploy, the database needs a one-off cleanup after it, and the monitoring stack needs an `infra` run for the new alert (see **Upgrade**).

### Fixed

- **A deleted user's name, phone and email stayed on their appointments and requests.** On `AuthEvent.UserDeleted`, appointments anonymized client details where `client_id` equalled the user id, but `client_id` is the business's client row id, so it never matched and nothing was cleared. Their pending requests weren't deleted either, for the same reason. Appointments now records the client's own account on every appointment and request (`client_user_id`, see **Changed**) and erases by it: the client name, phone, email and `client_user_id` are cleared on all of the user's appointments, and every request where they are the initiator, the client or the employee is deleted. Appointments booked for a client row before it had an account are covered too: when a user's first booking takes over a business's client row, or duplicates are merged into a user's row, business sends one `BusinessEvent.ClientLinked` naming the kept row, its account and the deleted duplicates, and appointments moves the duplicates' rows onto it and records the account on all of them. It replaces `BusinessEvent.ClientMerged`, whose moves could otherwise arrive after the link and leave moved rows without the account. That event can arrive after the deletion, so appointments keeps the ids of deleted accounts in `deleted_user` (`V10`, id and time only) and erases the rows it links when the account is already gone. A booking from an account whose deletion has already been handled is rejected with a 404, so it can't store the person's details after the erasure.
- **A deleted user's upcoming appointments stayed booked.** Their slots stayed blocked for a person who no longer exists. Every scheduled appointment of the deleted user that hasn't started yet is now cancelled with the reason "User deleted their account". Past appointments stay, anonymized, as the business's history. The employee assigned to each cancelled appointment gets a notification (`AppointmentEvent.CancelledForDeletedClient`, push, email or text by their settings, in English or Ukrainian) that a client deleted their account and the time is free again. It names no client, since their details are already erased, and no address, since the employee knows where they work.
- **A deleted employee's upcoming appointments stayed booked.** They kept a slot with an employee who no longer exists, renamed "Deleted User". Every scheduled appointment assigned to the deleted user that hasn't started yet is now cancelled with the reason "Employee deleted their account", and a client with an account gets the usual cancellation notification (`AppointmentEvent.Cancelled`, the employee named "Deleted User"). This is a stopgap until such appointments can be reassigned to another employee (#137). An appointment the user booked with themselves is cancelled with the client reason and notifies nobody. The cancelling read finds the user's rows as client or as employee through two indexes, `appointment_client_user_id` and the new `appointment_employee_user_id` (appointments `V11`, which also indexes `appointment_request.employee_user_id`); without the second one it scanned, and at REPEATABLE READ locked, the whole table, and so did renaming the employee on their appointments.
- **Anyone could book an instant appointment into any business, at any price.** `POST /api/appointments/instant` stored the `Appointment` from the request body after only the schedule checks: the business, employee, client, services and prices were all taken as sent, and the one authorization check compared the caller with `initiatorUserId` from the same body. Any signed-in user could fill another business's schedule, with no notification to anyone. The route now takes ids and a quote token only, resolves everything from business, and lets the caller book only where their appointments permission allows (see **Changed**). Appointments already booked that way are kept, without a client account on them (see **Changed**).
- **Approving a request could book a slot that was just taken.** `POST /api/appointments` read the caller's permission before locking the business's appointment settings. InnoDB fixes a transaction's snapshot at its first plain read, so an approval that waited for that lock behind another booking then checked overlaps against the data from before that booking committed, and both kept the same slot. It now locks the request and the settings before reading anything else.
- **Rescheduling could move an appointment onto a slot that was just taken.** `PUT /api/appointments/{id}` had the same order problem as approving: it read the permission before locking the business's appointment settings, so a reschedule that waited for that lock checked overlaps against data from before the other booking committed. It now checks the appointment and asks business for any new employee or services first, without holding a lock, then locks the settings and the appointment and checks everything again before writing. If the appointment changed in between, the route answers 409 `TRANSACTION_CONFLICT` (900001) and writes nothing; retry with fresh data.
- **Account deletion accepted any account's passkey.** `DELETE /api/auth/account` verified the passkey assertion but never checked whose passkey it was. The sign-in challenge names no user, so anyone holding a user's access token could delete that user's account with a passkey of their own. The passkey must now belong to the caller, or the request fails with 422 `PASSKEY_NOT_OWNED` (11). The route's documentation now lists its 422 errors and its real status, 204 (it said 200).
- **A deleted client stayed on the business's appointments.** `DeleteClient` removed the business's client row, but appointments kept that client's name, phone and email on every appointment and request booked for them, and their upcoming appointments stayed booked. Business now announces the deletion with `BusinessEvent.ClientDeleted`, and appointments erases the client from that business only, matched by business and client id: their upcoming scheduled appointments are cancelled with the reason "Client was removed by the business", every appointment of theirs there shows "Deleted Client" with no phone, email or account, and their requests there are deleted. The employee of each cancelled appointment is told the business removed the client (`AppointmentEvent.CancelledForDeletedClient`, `cause = REMOVED_BY_BUSINESS`), and a client with an account gets the usual cancellation notification with that reason. Appointments keeps the deleted client's id in `deleted_client` (`V10`, client id, business id and time only): a `BusinessEvent.ClientLinked` for that client that arrives late erases the rows it links, and both booking paths reject a booking for the deleted client with a 404, so a booking that read the client's details just before the delete can't store them afterwards. Clients deleted before this release need the cleanup in **Upgrade**.
- **Clients never received approval, rejection or cancellation notifications.** `AppointmentEvent.RequestApproved`, `RequestRejected` and `Cancelled` carried the business's client row id as `clientUserId`, and the notifications service looked up settings and devices for a user with that id, which never exists. They now carry the client's own account (`client.userId`, see **Changed**), so these notifications reach the client, and a client without an account is simply not notified. `RequestCreated` carried the same wrong id but is sent to the employee, so it had no visible effect. Combined with the notification fix below, this is the first release in which clients get these notifications at all.
- **The new-request email to an employee no longer lists the business address.** It is the employee's own workplace. The `address` field stays in `AppointmentEvent.RequestCreated`, unused, so the event's wire format is unchanged.
- **Device language changes and notifications failed on every delivery.** The notifications service handled `AuthEvent.DeviceLanguageUpdated` and every notification event (`AppointmentEvent.RequestCreated`, `RequestApproved`, `RequestRejected`, `Cancelled`, `BusinessEvent.EmployeeInvitationRedeemed`) by reading or writing the database outside a transaction, so each attempt failed with `IllegalStateException: No transaction in context.` A device kept the language it first signed in with, and no appointment or invitation notification was sent. Both now run in a transaction. The channels still send outside it, so a conflict retry never sends a notification twice.
- **An event that failed every attempt was retried forever.** After the last DLT attempt, both consumers (embedded and Kafka) save the event to `exhausted_event`, but that save also ran outside a transaction. It always failed, logged `Failed to persist exhausted event for topic: … Retrying. Error: … No transaction in context.`, and put the event back on the DLT with a higher attempt count, indefinitely. The save now runs in a transaction, so failing events land in `exhausted_event` as designed.
- **The API documentation listed wrong auth error codes.** The OpenAPI descriptions of sign-in, registration, refresh and the passkey routes gave numbers from other services' blocks (`CHALLENGE_WINDOW_EXPIRED (300001)`, `EMAIL_EXIST (200001)`, `INVALID_CREDENTIALS (400001)`, …). The server always sent auth's own codes, which the documentation now shows: `EMAIL_EXIST` 1, `INVALID_EMAIL_FORMAT` 2, `USER_ALREADY_EXIST` 3, `VERIFICATION_FAILED` 4, `ACCOUNT_CREATION_FAILED` 5, `CHALLENGE_WINDOW_EXPIRED` 6, `PASSKEY_OWNER_NOT_FOUND` 7, `INVALID_CREDENTIALS` 8, `LAST_PASSKEY` 9, `UNABLE_TO_GENERATE_REGISTRATION_CHALLENGE` 10. An app that matched the documented numbers should match these instead.
- **Passkey requests no longer log a warning per Android origin.** Every passkey registration and sign-in logged `Allowed origin is not a valid URL, it will match only by exact string equality` twice, once for each `android:apk-key-hash:` origin. Those origins are meant to be matched exactly, so `logback.xml` now drops that one message. The relying party's other warnings still appear.

- **Deleting an account left its sign-in data behind.** `DeleteAccount` passed the user id to `AccountDataSource.deleteAuthorization`, which deletes by the account's own id, so nothing in auth was deleted: the account row, its passkeys and its devices all stayed, and the person could sign in again with their passkey to an account whose profile was gone. The account row is now deleted by its id, and its passkeys and devices go with it (foreign keys with cascade).

### Added

- **Passkey step-up for sensitive actions.** `POST /api/auth/passkey/step_up` takes a fresh assertion of one of the caller's own passkeys (challenge from `GET /api/auth/passkey/sign_in/challenge`) together with an action (`StepUpAction.DELETE_CLIENT`), a business id and a target id, and returns a single-use token valid for 2 minutes (`StepUpToken`). It is a JWT signed with auth's keys, with its own audience (`<domain>/step-up`), so it never works as an access token and an access token never works as a step-up token. Errors are 422 `CHALLENGE_WINDOW_EXPIRED` (6), `VERIFICATION_FAILED` (4), `PASSKEY_OWNER_NOT_FOUND` (7) and the new `PASSKEY_NOT_OWNED` (11). See `docs/operations/authorization/step-up.md`.
- **Parked events are replayed.** An event parked in a service's `exhausted_event` table used to stay there until someone re-published it by hand, and nothing did. A parked `AuthEvent.UserDeleted` meant an erasure that never ran. Every start of the app now replays every event parked before it, so deploying a fix heals what the bug parked. An operator can also request a replay without a deploy, with `UPDATE <schema>.exhausted_event SET replay_requested = TRUE WHERE original_topic = '<topic>'`, which takes effect within a minute. A replayed event goes back only to the consumer that failed it, through that consumer's own DLT topic, with all 3 attempts again. If it fails again, it is parked again as a new row. A row the running version can't replay any more (its topic has no receiver, or its payload no longer decodes) is kept, logged as `Cannot replay a parked event`, and not retried. Handle it by hand and delete it. See `docs/exhausted-events.md`.
- **Encrypted database backups with point-in-time recovery.** Prod had no backups. A new `backup` stack on the manager keeps 30 days in `/srv/backups/bookk`: a nightly dump of the five schemas, and the database's binary log, stored every 5 minutes. A restore can return to any moment in those 30 days, losing at most the last 5 minutes, and restoring to the latest point replays every account and client deletion. Every file is compressed and encrypted with GnuPG to a public key in the repository, and the private key stays offline with the operator, so a compromised server can't read them. `BackupDumpMissing` and `BinlogShippingStalled` alert when either part falls behind, and the Infrastructure dashboard shows the days backed up, the space used and how recent both parts are. `make fetch-backups` and `make restore-drill` copy and test-restore them on the operator's machine. See Backups in `deployment/ansible/README.md`.
- **A business decides how long it keeps a client who stops visiting.** The business is the controller of its clients' data, so it sets the retention period as a business setting: `Business.clientDataRetentionMonths` (`@ProtoNumber(14)`, column `business.client_data_retention_months`, business `V17`), 36 months by default, sent through `PUT /api/business/{id}` as `BusinessUpdateModel.clientDataRetentionMonths` (`@ProtoNumber(10)`, nullable, `null` keeps the stored value, so older apps don't reset it). A value outside 12 to 120 months is rejected with 422 `BUSINESS_CLIENT_DATA_RETENTION_OUT_OF_RANGE` (200041). Each client row now records its last activity (`client.last_activity_at`, business `V17`, indexed with `business_id`): when it was created, edited or taken over by a booking, moved forward to the end of every visit booked or rescheduled for it. Appointments reports those visits with a new event, `AppointmentEvent.ClientVisitBooked(businessId, clientId, visitEnd)`, sent by booking requests, instant bookings, approvals (automatic ones included) and reschedules, and business consumes it. A new daily job, `business-deleteInactiveClients`, deletes every client whose last activity is older than their business's period and sends `BusinessEvent.ClientDeleted` for each, so appointments erases them exactly as when the business deletes a client: "Deleted Client" on their appointments, no phone, email, account or note, requests deleted. A client with an upcoming appointment is never deleted. The client's own account is not touched; if they book again, a new client row is created as for anyone else.
- **Parked events raise an alert.** The app exports the parked rows as `events_exhausted{consumer_group, topic}`, refreshed every minute, and Prometheus fires `EventsExhausted` (critical) while any exist. The alert's description has the replay steps.

- **Personal data that served its purpose is deleted on a schedule.** Requests whose slot ended more than 60 days ago are deleted daily, whatever their status (`appointments-deleteExpiredRequests`, using a new index on `appointment_request.date_end`, appointments `V10`). The ids in `deleted_user` and `deleted_client` are deleted 60 days after the deletion they record, twice the 30-day backup window (`appointments-deleteExpiredTombstones`). Events parked in any service's `exhausted_event` are deleted 30 days after they were parked, since their payload can hold personal data (checked every minute by the replayer, logged as a warning).

- **An account nobody uses for 3 years is deleted, after three warnings.** Auth now records each account's last activity (`authentication.last_active_at`, authorization `V4`) on every token refresh and sign-in, at most once a day. A new daily job, `auth-handleDormantAccounts`, sends `AuthEvent.AccountDormancyWarning` 60 days before the 3 years are up, again 30 days before, and a final one 7 days before (`final = true`, rendered as the last reminder; every warning carries the deletion date), and then deletes the account exactly as if the user had deleted it (`AuthEvent.UserDeleted`, every service erases them). Any refresh or sign-in in between cancels it. An owner is kept while any other member of their business has been active within the 3 years: auth asks business through a new internal route, `GET /api/internal/owner/{userId}/members`. Notifications sends the warning as a new `ACCOUNT` notification, which the user can't switch off, in English and Ukrainian. It goes through the channels the user has enabled; push rarely reaches someone inactive this long, because devices and their push tokens are deleted after 30 days without sign-in, and email will carry it once email sending exists.

- **The server records which legal documents each user accepted.** The user service keeps the published versions of the privacy policy and the terms of service (`legal_document_version`, user `V6`: document, version, link, time it takes effect), inserted by the operator with SQL (see `docs/legal-documents.md`), and every version each user accepted, with the time (`legal_document_acceptance`, append-only, deleted with the account). `GET /api/user/legal` (no authentication) returns the version in effect of each document (`LegalDocumentVersion`: `document` 1, `version` 2, `url` 3, `effectiveAt` 4), `GET /api/user/me/legal` returns the same versions with the time the caller accepted each, or none (`LegalDocumentStatus`: `current` 1, `acceptedAt` 2), and `POST /api/user/me/legal` records the caller's acceptance (`AcceptedLegalDocuments`: `privacyPolicy` 1, `termsOfService` 2, both nullable, a missing one is left as it is) and answers 204. Sign-up must accept them too (see **Changed**). Only a published version already in effect can be accepted, otherwise 422 `LEGAL_DOCUMENT_VERSION_UNKNOWN` (100003). Accepting a version again keeps the first time. After sign-up, the server doesn't block a user who hasn't accepted a newer version in effect; the app asks them to. A document with no published version is left out of both read routes.

- **A backup restore no longer brings deleted people back.** Every account deletion (including the dormant-account job) and every client deletion (including the retention job) now writes a record in its own transaction: `authorization.erased_account` (auth `V5`) and `business.erased_client` (business `V18`), ids and time only, kept 60 days, hidden from NocoDB. Restoring to an earlier point with `scripts/backup-restore.sh sql --until` first restores the latest point in a throwaway container, reads every record and appends it to the restore, marked `reapply_requested` where the restored database lacks it. At its next start the app deletes those accounts and clients again (`auth-reapplyErasures`, `business-reapplyErasures`, every minute from start) and sends the usual `UserDeleted`/`ClientDeleted`, so every service erases them as the first time. `make restore-drill until=…` reports how many a restore would bring back. Two daily jobs delete records older than 60 days.

### Changed

- **Sign-up requires accepting the legal documents in effect.** `POST /api/auth/sign_up` takes `VerifyAccountCreationRequest.acceptedDocuments` (`AcceptedLegalDocuments`, `@ProtoNumber(5)`, required), which must name the version in effect of every published document, as `GET /api/user/legal` returns them, and is recorded with the new account in the same transaction. A missing or earlier version is 422 `LEGAL_DOCUMENTS_NOT_ACCEPTED` (100004), a version that isn't published or not yet in effect is 422 `LEGAL_DOCUMENT_VERSION_UNKNOWN` (100003), and in both cases no account is created. A document with no published version needs nothing, so an empty `AcceptedLegalDocuments` signs up until the first version is published. An app that doesn't send the field gets 400: every app version released so far can no longer sign up once this release is deployed, until the user updates the app. Signing in is not affected.
- **Instant booking takes a draft, and follows the appointments permission.** `POST /api/appointments/instant` is the route for an employee booking an existing client of the business; an employee booking themselves books like any client, through `POST /api/appointments/request`. It now takes `InstantAppointmentDraft` (`businessId` 1, `employeeId` 2, `services` 3 as service id and count, `date` 4, `note` 5, `offerToken` 6, `clientId` 7) instead of a whole `Appointment`, the same fields as an appointment request draft plus the client. The app gets the quote token from `POST /api/business/{businessId}/service/quote` as it does for requests, and the client must already be a client of the business (create a walk-in client first). Business resolves the employee, the client and the services through a new internal route, `POST /api/internal/business/{id}/staff-booking-context`, which only reads. A caller with the appointments update grant may book any employee of the business, a caller with only the view grant only themselves, anyone else gets 404, and a suspended employee 403 `BUSINESS_EMPLOYEE_ACCESS_SUSPENDED` (200034). `INSTANT_APPOINTMENT_TO_OTHER` (300015) is removed. New errors on this route: 400 `PRICE_CHANGED` (300013), `DURATION_CHANGED` (300017) and `SERVICES_VALIDATION_FAILED` (300014), 422 `QUOTE_TOKEN_ALREADY_USED` (300016), `BUSINESS_EMPLOYEE_SUSPENDED` (200033), `BUSINESS_QUOTE_SERVICE_NOT_FOUND` (200013) and `BUSINESS_QUOTE_EMPTY_SERVICE_LIST` (200014), 404 `BUSINESS_EMPLOYEE_NOT_EXISTS` (200024) and `BUSINESS_CLIENT_NOT_EXISTS` (200006). The appointment's client account is the client row's own account, and a booking for a client whose account or client row was deleted is a 404. Older app versions still send an `Appointment`, which doesn't decode as a draft, so they get 400 until they update.
- **Deleting a client needs a passkey confirmation.** `DELETE /api/business/{businessId}/clients/{id}` now takes a body, `DeleteClientRequest(stepUpToken)`, with a step-up token issued for the caller, `DELETE_CLIENT`, that business and that client. A missing, expired, already used or mismatched token is refused with 403 and the new `BUSINESS_STEP_UP_INVALID` (200040) before anything is read. A request without a body gets the same 403, so app versions that don't send the token yet can't delete clients until they update. Each token works once: business spends its id in Valkey (`step_up:<jti>`), and gives it back if the deletion then fails.
- **Only the owner can delete a client with an account.** Deleting an integrated client (one linked to a user's account) erases that user's booking history at the business, so an employee with the `CLIENTS` delete grant now gets a 404 for it, like any other permission failure. Clients without an account can still be deleted by anyone with the grant. The person keeps their account and their history at other businesses. A self-service "remove me from this business" for users is tracked in #138.
- **An appointment can no longer be changed a week after it ends.** Rescheduling, cancelling, completing (with or without a price adjustment) and marking as no-show are refused with 422 `APPOINTMENT_EDIT_WINDOW_CLOSED` (300026) once the appointment ended more than 7 days ago, whatever its status. Within the week everything works as before, including adjusting the price of an appointment that automatic completion already completed, or switching it to no-show. Repeating a completion or a no-show on an older appointment, which used to return it unchanged, now gets the 422 too. An appointment a business with automatic completion off never closed stays `SCHEDULED`.
- **Client and account deletion also clear the appointment note** on the person's appointments, along with their name, phone, email and account. The cancellation reason and the price adjustment reason stay as the business's record.
- **`AppointmentEvent.CancelledForDeletedClient` has a `cause`** (`@ProtoNumber(8)`, `ACCOUNT_DELETED` by default or `REMOVED_BY_BUSINESS`), which picks the employee's wording. The event is new in this release, so nothing older reads it.
- **`Appointment.userId` and `AppointmentRequest.userId` are now `initiatorUserId`** (column `initiator_user_id`, `V10`). It always meant who booked, not who the client is. The field keeps `@ProtoNumber(2)`, so the ProtoBuf bytes are unchanged, but generated client code and the OpenAPI schema use the new name.
- **`ClientSnapshot.userId` (`@ProtoNumber(5)`, nullable) is the client's own account** (column `client_user_id`, indexed, `V10`), empty for a client without one and cleared when the client is erased. It is resolved server-side from business, for requests and instant appointments alike: the booking client's own account, never the body or the caller. `V10` fills it from `initiator_user_id` for existing requests, which clients made themselves. Existing appointments were all booked by staff through the instant route, so their `initiator_user_id` is the employee and `V10` leaves their `client_user_id` empty. Deleting a client's account therefore doesn't reach those appointments (deleting the client in the business still does, by `client_id`), and their cancellations notify no client.
- **The Redis client is Lettuce 7.9.0** (was 6.4.0), with its connection pool on commons-pool2 2.13.1 (was 2.4.3). Lettuce 7 is built on Netty 4.2, which the app already ran on through its other dependencies (now 4.2.17). No configuration changes; the cache, idempotency and event-claim calls behave as before against Valkey 8.1.11.

- **Account deletion deletes the person's client and employee rows in business** instead of anonymizing them, which left a nameless row behind and kept the business's `description` of the client and, on the employee row, the account id. The client rows go in every business, the employee rows together with their services, working hours, days off and permission grants (all removed by their foreign keys). The appointments booked for them keep their own erased copy of the details.

- **Account deletion keeps the requests the person only booked for someone else.** Requests where the deleted user is the client or the employee are still deleted. A request they booked for another client now stays, with its `initiatorUserId` set to the nil UUID, so it no longer points to them.
- **Account deletion leaves no pointer to the person on appointments.** Appointments they booked get the nil UUID as `initiatorUserId`, and appointments they worked as employee get it as `employee.userId` (next to the name "Deleted User"). Both fields keep their type and `@ProtoNumber`, so older apps decode them as before. The `AppointmentEvent.Cancelled` sent to a client when their employee deleted the account now names the employee "Deleted User", as stored on the appointment, instead of "Deleted Employee".

### Deployment

- **`BusinessEvent.ClientMerged` is gone.** Nothing consumes it any more, so a `ClientMerged` still in the outbox or `exhausted_event` at deploy time is not applied. Prod has almost no clients, so none is expected; if in doubt, check before deploying with `SELECT COUNT(*) FROM business.outbox_event WHERE topic = 'business.client_merged'` and `SELECT COUNT(*) FROM appointments.exhausted_event WHERE original_topic = 'business.client_merged'`.
- **`V10` renames a column the running version reads.** The monolith deploys `start-first`, so for the few seconds both tasks run, the old task's appointment reads and writes fail with `Unknown column 'user_id'` until it stops. Deploy outside busy hours.
- **Public traffic goes through Cloudflare.** The domain's records are proxied by Cloudflare, so nginx receives public requests from Cloudflare's edge. Port 443 should admit only Cloudflare's published ranges in the provider firewall, since nginx trusts the client address those ranges send.
- **The certificate is issued through Cloudflare DNS.** acme.sh now solves the Let's Encrypt DNS challenge with `dns_cf` and the new vault secret `CLOUDFLARE_DNS_API_TOKEN`, instead of Hostinger (`HOSTINGER_API_KEY` and the bundled `dns_hostinger.sh` are gone). Its state now lives in `/acme.sh` on the `acmesh-data` volume. It used to be mounted at a path acme.sh never reads, so every new nginx container requested a new certificate, and Let's Encrypt allows only 5 identical ones per week. A restart now reuses the saved certificate, and the daily cron renews it.
- **nginx logs the real client address.** `snippets/cloudflare-real-ip.conf` trusts Cloudflare's ranges and takes the client address from `CF-Connecting-IP`. Only the public `${DOMAIN_NAME}` server block includes it, so the VPN-only hosts still check the actual TCP peer. Every `make infra tags=nginx` compares the snippet with Cloudflare's live lists and fails when they differ. In that case, regenerate the snippet with `scripts/cloudflare-real-ip.sh` and update the firewall to the same ranges.
- **nginx reloads instead of restarting.** A restart dropped HTTPS for a few seconds, because 443 is published in `mode: host`. The templates, snippets and well-known files are now read-only directory bind mounts from the stack directory, not swarm configs. They moved to `templates/services.conf.template` (was `nginx.conf.template`) and `static/` (the two well-known files). When only those files change, `make infra tags=nginx` renders them in the running container, runs `nginx -t` and reloads. If the test fails, the run fails and the previous configuration keeps serving. A change to the service itself (image, env, ports, networks) still restarts nginx. The swarm configs `nginx-v47`, `android_well_known-v3`, `ios_well_known-v5` and `acme_sh_hostinger_dns-v1` are no longer used.
- **The `identity.` host is gone.** Its nginx server block proxied to a Keycloak that was never deployed. Dev's commented-out Keycloak service and its database user are removed too. nginx's `listen … http2` directives became `http2 on`, which removes nginx's deprecation warning.
- **WireGuard's endpoint is the manager's public IP.** Cloudflare's proxy carries no UDP, so `vpn.<domain>` can no longer be the endpoint. `WG_HOST` is now `VPN_ENDPOINT`, the first manager's address from `vault_hosts`, and the public `vpn.` record isn't needed. Inside the tunnel, dnsmasq still resolves `vpn.<domain>` to the panel.
- **Every swarm node joins the VPN.** Each host in `inventory/prod.yml` names its client and address (`vpn_client`, `vpn_address`): the manager stays `admin-1` on `.2`, and `worker-1` gets `.4`, reachable from VPN clients without a public port. These replace `vpn_host_client`, and `admin-1` is no longer in `vpn_clients`. `tags=vpn` must run on the manager too: the workers' configs come from its run, so don't `--limit` it out.
- `deployment/prod/.env.example` is removed. The vault (`vault.example.yml`) lists every value a deployment needs.

- **No client is deleted at the first start.** Existing clients get the migration time as their last activity (`V17`), so the inactive-client job deletes none of them before a full retention period has passed (36 months by default, or the period a business sets). Visits booked before this release don't count towards the activity, only the migration time does, which only ever keeps clients longer.
- **The `exhausted_event` migrations only add a column** with a default, so the old task keeps working during the `start-first` deploy.
- **The first start replays everything already parked.** Before deploying, list what is there with `SELECT original_topic, createdAt FROM <schema>.exhausted_event ORDER BY createdAt` in each of the five schemas. Every row is replayed once on the first start. Delete the rows that would do harm late: a notification event in `notifications` (`appointment.request_created`, `appointment.request_approved`, `appointment.request_rejected`, `appointment.cancelled`, `business.employee_invitation_redeemed`) would reach its user days or weeks after the fact. Rows that change data (a `UserDeleted`, a profile update) should stay, since replaying them is the point. A `business.client_merged` row can't be replayed (nothing consumes that topic any more), so it stays and raises the alert. Delete it once the `ClientMerged` check above is done.
- The Prometheus rules are a new swarm config, `prometheus_alerts_cfg-v8`, and the Infrastructure dashboard is `grafana_dashboard_infrastructure-v2`.
- **The database now writes a binary log**, needed for the backups (`log_bin = binlog`, row format, kept 2 days, in `monolith-mariadb-tuning-v3`), and gets a `backup` user (`monolith-mariadb-users-init-v3`, password `vault_monolith_db_backup_password`). The first deploy with these restarts the database once. Every monolith deploy, CI's included, renders that user from the vault, so commit the vault after `make vault-update` before the release tag, or the deploy fails on the missing password.
- **NocoDB reads fewer tables.** `nocodb_reader` loses `SELECT` on the five whole schemas and keeps it only on the tables listed in `users-init.sql.j2`: nothing in `authorization`, and no `outbox_event`, `exhausted_event`, `signing_key`, `employee_invitation`, `deleted_user`, `deleted_client` or `flyway_schema_history`. Its NocoDB source for `authorization` stops working, and the other sources lose those tables. The change comes with the same `monolith-mariadb-users-init-v3` secret and database restart as the `backup` user. A table that doesn't exist yet when the database starts is skipped, so user `V6`'s `legal_document_version` and `legal_document_acceptance` reach NocoDB only after the database restarts once more following this release's deploy.
- **Migrations can be rehearsed on prod's data before a deploy.** `make migration-rehearsal` (after `make fetch-backups`) restores the latest backup into a throwaway container on the operator's machine, runs the checkout's migrations with Flyway 13.1.0, and lists every table whose row count changed. `deployment/ansible/README.md` also gained the recovery steps for a migration that fails in prod. The rehearsal and the restore drill keep the restored database in memory (tmpfs), never on the disk of Docker's VM.
- **NocoDB can publish legal document versions.** A second MariaDB user, `nocodb_writer` (password `vault_monolith_db_nocodb_writer_password`, generated by `make vault-update`, at most 5 connections), holds `SELECT` and `INSERT` on `user.legal_document_version` and nothing else: no `UPDATE` or `DELETE` there, no access to any other table. Like the `backup` user it is rendered from the vault on every monolith deploy, so commit the vault before the release tag.

- **The first start deletes old requests and old parked events.** Requests whose slot ended more than 60 days ago and events parked more than 30 days ago go as soon as the monolith starts, and only a backup restore brings them back. Check first with `SELECT COUNT(*) FROM appointments.appointment_request WHERE date_end < NOW() - INTERVAL 60 DAY` and, per schema, `SELECT COUNT(*) FROM <schema>.exhausted_event WHERE createdAt < NOW() - INTERVAL 30 DAY`.

- **No account is deleted for inactivity at the first start.** Existing accounts get the migration time as their last activity (authorization `V4`), so the first warnings go out about 2 years and 10 months after this release.
- **A standalone auth service needs `APPLICATION_BUSINESS_SERVICE_HOSTNAME`** (added to `deployment/dev/service/auth.env`), since it now calls the business service. The monolith already has it.
- **User `V6` only adds tables**, so the old task keeps working during the `start-first` deploy. The internal `POST /api/internal/user` now takes a `NewUser`, with the accepted documents required, instead of a `User`, so while both tasks run a sign-up fails whenever the swarm routes it between an old and a new task (400 one way, a missing `id` the other). A sign-up in those few seconds may need a retry.
- **Release the app update that sends `acceptedDocuments` before, or together with, this deploy.** From the deploy on, older apps get 400 on sign-up.

### Upgrade

1. Set up the backups before anything else, so that this release's migrations run with a backup in place. Follow Setting it up under Backups in `deployment/ansible/README.md`: create the key pair and commit the public key, `make vault-update`, `make infra tags=swarm`, `make deploy v=0.16.1` (binary logging and the `backup` user, one database restart), `make bootstrap tags=backup`, `make infra tags=monitoring`, then `make fetch-backups && make restore-drill`. Commit the vault and the public key before tagging this release.
2. Create a Cloudflare API token with Zone Read and DNS Edit on the zone. Run `make vault-update`, which adds an empty `CLOUDFLARE_DNS_API_TOKEN`, then set it with `make vault-edit`. `HOSTINGER_API_KEY` can be deleted.
3. Move the domain's DNS to Cloudflare with proxied records, and restrict 443 in the provider firewall to Cloudflare's ranges (`https://www.cloudflare.com/ips-v4`, `ips-v6`). A DNS-only `vpn.` record is not needed, and it would publish the manager's IP.
4. `make infra tags=vpn`, without `--limit`. It creates `worker-1`'s client and brings up `wg0` on the worker. Re-import every device's WireGuard config from `deployment/ansible/vpn-clients/`: the existing configs point at `vpn.<domain>`, which no longer reaches the manager over UDP.
5. `make infra tags=nginx`. The service spec changes this time (mounts, env), so nginx restarts once, and its first start issues a new certificate into the relocated `acmesh-data` path. Later changes to the templates only reload.
6. Rehearse this release's migrations on prod's data: from the release checkout, `make fetch-backups && make migration-rehearsal` in `deployment/ansible`. It runs the nine new migrations on a throwaway restore of the latest backup and should list exactly six new, empty tables (`appointments.deleted_client`, `appointments.deleted_user`, `authorization.erased_account`, `business.erased_client`, `user.legal_document_acceptance`, `user.legal_document_version`) and no other change. Anything else, or a failure, stops the release. Then deploy the monolith. Events that were looping when the old version stopped were held only in memory and are gone: a device keeps its old language until its next sign-in, and notifications sent before the deploy are not resent.
7. The `EventsExhausted` alert was deployed with the backup alerts in step 1 (`prometheus_alerts_cfg-v8`). Its metric appears as soon as the new version runs.
8. Erase clients that businesses deleted before this release. Their appointments and requests still point to a `client_id` that no longer exists in `business.client`. Check first, as the `monolith` user, what would change:
   ```sql
   SELECT a.business_id, a.client_id, COUNT(*) FROM appointments.appointment a
   LEFT JOIN business.client c ON c.id = a.client_id AND c.business_id = a.business_id
   WHERE c.id IS NULL GROUP BY a.business_id, a.client_id;
   ```
   Every row is a client the business deleted, unless an old `BusinessEvent.ClientMerged` was lost before it moved that client's appointments onto the kept row. In that case the rows belong to a client who still exists, and they need moving by hand instead. If the rows are all deleted clients, run once (running it again changes nothing):
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
   It sends no notifications.
   `status` is stored as the enum's position: `0` is `SCHEDULED`, `2` is `CANCELLED`.
9. Restart the database once outside busy hours (`docker --context swarm-manager service update --force monolith_database`, a few seconds without database), so the grants on user `V6`'s two tables, created by step 6's deploy, are applied. Then, in NocoDB, delete the `authorization` source, sync the metadata of the other four so they drop the tables they can no longer read, and add one more source for the `user` schema logging in as `nocodb_writer` (SSL on, password from `make vault-view`). It shows only `legal_document_version`.
10. Once the privacy policy and the terms of service are on the website, publish their versions through that `nocodb_writer` source, one row per document in `user.legal_document_version` (the columns are in `docs/legal-documents.md`), as soon as each text is live at its link. From that moment every sign-up must accept it. Until then both read routes return nothing and sign-up needs no acceptance. A version can't be edited or deleted once it is inserted, by `nocodb_writer` not at all, so check the link first.

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

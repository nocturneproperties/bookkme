# Release notes

## 0.16.2

Changes since `monolith/0.16.1`. A patch release: notifications handle their events again, an event that keeps failing is parked instead of retried forever, and public traffic now goes through Cloudflare. No API changes and no migrations, but the infrastructure needs a new secret and an `infra` run before the deploy (see **Upgrade**).

### Fixed

- **Device language changes and notifications failed on every delivery.** The notifications service handled `AuthEvent.DeviceLanguageUpdated` and every notification event (`AppointmentEvent.RequestCreated`, `RequestApproved`, `RequestRejected`, `Cancelled`, `BusinessEvent.EmployeeInvitationRedeemed`) by reading or writing the database outside a transaction, so each attempt failed with `IllegalStateException: No transaction in context.` A device kept the language it first signed in with, and no appointment or invitation notification was sent. Both now run in a transaction. The channels still send outside it, so a conflict retry never sends a notification twice.
- **An event that failed every attempt was retried forever.** After the last DLT attempt, both consumers (embedded and Kafka) save the event to `exhausted_event`, but that save also ran outside a transaction. It always failed, logged `Failed to persist exhausted event for topic: … Retrying. Error: … No transaction in context.`, and put the event back on the DLT with a higher attempt count, indefinitely. The save now runs in a transaction, so failing events land in `exhausted_event` as designed.
- **Passkey requests no longer log a warning per Android origin.** Every passkey registration and sign-in logged `Allowed origin is not a valid URL, it will match only by exact string equality` twice, once for each `android:apk-key-hash:` origin. Those origins are meant to be matched exactly, so `logback.xml` now drops that one message. The relying party's other warnings still appear.

### Deployment

- **Public traffic goes through Cloudflare.** The domain's records are proxied by Cloudflare, so nginx receives public requests from Cloudflare's edge. Port 443 should admit only Cloudflare's published ranges in the provider firewall, since nginx trusts the client address those ranges send.
- **The certificate is issued through Cloudflare DNS.** acme.sh now solves the Let's Encrypt DNS challenge with `dns_cf` and the new vault secret `CLOUDFLARE_DNS_API_TOKEN`, instead of Hostinger (`HOSTINGER_API_KEY` and the bundled `dns_hostinger.sh` are gone). Its state now lives in `/acme.sh` on the `acmesh-data` volume. It used to be mounted at a path acme.sh never reads, so every new nginx container requested a new certificate, and Let's Encrypt allows only 5 identical ones per week. A restart now reuses the saved certificate, and the daily cron renews it.
- **nginx logs the real client address.** `snippets/cloudflare-real-ip.conf` trusts Cloudflare's ranges and takes the client address from `CF-Connecting-IP`. Only the public `${DOMAIN_NAME}` server block includes it, so the VPN-only hosts still check the actual TCP peer. Every `make infra tags=nginx` compares the snippet with Cloudflare's live lists and fails when they differ. In that case, regenerate the snippet with `scripts/cloudflare-real-ip.sh` and update the firewall to the same ranges.
- **nginx reloads instead of restarting.** A restart dropped HTTPS for a few seconds, because 443 is published in `mode: host`. The templates, snippets and well-known files are now read-only directory bind mounts from the stack directory, not swarm configs. They moved to `templates/services.conf.template` (was `nginx.conf.template`) and `static/` (the two well-known files). When only those files change, `make infra tags=nginx` renders them in the running container, runs `nginx -t` and reloads. If the test fails, the run fails and the previous configuration keeps serving. A change to the service itself (image, env, ports, networks) still restarts nginx. The swarm configs `nginx-v47`, `android_well_known-v3`, `ios_well_known-v5` and `acme_sh_hostinger_dns-v1` are no longer used.
- **The `identity.` host is gone.** Its nginx server block proxied to a Keycloak that was never deployed. Dev's commented-out Keycloak service and its database user are removed too. nginx's `listen … http2` directives became `http2 on`, which removes nginx's deprecation warning.
- **WireGuard's endpoint is the manager's public IP.** Cloudflare's proxy carries no UDP, so `vpn.<domain>` can no longer be the endpoint. `WG_HOST` is now `VPN_ENDPOINT`, the first manager's address from `vault_hosts`, and the public `vpn.` record isn't needed. Inside the tunnel, dnsmasq still resolves `vpn.<domain>` to the panel.
- **Every swarm node joins the VPN.** Each host in `inventory/prod.yml` names its client and address (`vpn_client`, `vpn_address`): the manager stays `admin-1` on `.2`, and `worker-1` gets `.4`, reachable from VPN clients without a public port. These replace `vpn_host_client`, and `admin-1` is no longer in `vpn_clients`. `tags=vpn` must run on the manager too: the workers' configs come from its run, so don't `--limit` it out.
- `deployment/prod/.env.example` is removed. The vault (`vault.example.yml`) lists every value a deployment needs.

### Upgrade

1. Create a Cloudflare API token with Zone Read and DNS Edit on the zone. Run `make vault-update`, which adds an empty `CLOUDFLARE_DNS_API_TOKEN`, then set it with `make vault-edit`. `HOSTINGER_API_KEY` can be deleted.
2. Move the domain's DNS to Cloudflare with proxied records, and restrict 443 in the provider firewall to Cloudflare's ranges (`https://www.cloudflare.com/ips-v4`, `ips-v6`). A DNS-only `vpn.` record is not needed, and it would publish the manager's IP.
3. `make infra tags=vpn`, without `--limit`. It creates `worker-1`'s client and brings up `wg0` on the worker. Re-import every device's WireGuard config from `deployment/ansible/vpn-clients/`: the existing configs point at `vpn.<domain>`, which no longer reaches the manager over UDP.
4. `make infra tags=nginx`. The service spec changes this time (mounts, env), so nginx restarts once, and its first start issues a new certificate into the relocated `acmesh-data` path. Later changes to the templates only reload.
5. Deploy the monolith. Events that were looping when the old version stopped were held only in memory and are gone: a device keeps its old language until its next sign-in, and notifications sent before the deploy are not resent.

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

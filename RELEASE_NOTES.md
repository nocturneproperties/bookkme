# Release notes

## 0.15.12

Changes since `monolith/0.15.11`. A patch release: there are no API, wire-format or schema changes, so clients need no update.

### Deployment

- **The database and the cache have their own network.** MariaDB and Valkey moved from `operational` to a new encrypted overlay, `storage`, which only the app, the two exporters and NocoDB join. Before, every container on `operational` could reach them, including nginx and the VPN stack, and needed only a password.
- **The app connects to MariaDB over verified TLS.** Flyway and the connection pool connect with `sslMode=verify-full`. MariaDB 11.8 generates its own certificate, and the driver verifies it against the login password, so there are no certificates to manage. MariaDB now refuses unencrypted connections (`require_secure_transport`, config `monolith-mariadb-encryption-v2`), and `database-exporter` connects with `--tls.insecure-skip-verify`.

### Upgrade

1. `make infra tags=swarm` creates the `storage` network. The monolith deploy fails until it exists.
2. Deploy the monolith. The new config restarts the database once, so expect a few seconds without the database.
3. `make infra tags=admin` moves NocoDB onto `storage`.
4. In NocoDB, turn on SSL for each MariaDB source. Until then the admin panel can't read the database, since plaintext connections are refused.

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

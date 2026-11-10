# BookkMe Server

**BookkMe** is an appointment-booking platform for small businesses such as salons, barbers, studios, clinics and tutors. Clients find a business, pick a service and a time, and get notified about their booking. The business manages its services, staff, working hours, clients and bookings in one place.

This repository holds the BookkMe backend. You can run it yourself to take bookings for your own business, study how it works, or change it to fit your needs, within the terms of the [license](#license).

---

## Contents

- [Features](#features)
- [Try it locally](#try-it-locally)
- [Self-hosting for your business](#self-hosting-for-your-business)
- [Configuration](#configuration)
- [Running as microservices](#running-as-microservices)
- [How the services communicate](#how-the-services-communicate)
- [Security](#security)
- [Privacy and GDPR](#privacy-and-gdpr)
- [Upgrading](#upgrading)
- [Building from source](#building-from-source)
- [Project layout](#project-layout)
- [Support](#support)
- [Feature requests](#feature-requests)
- [Contributing](#contributing)
- [License](#license)

---

## Features

**For businesses**

- Services and service groups, each with its own price and duration
- Staff accounts with invite codes and separate view, edit and delete permissions for business details, staff, clients, services and appointments, plus suspending access
- Working hours, days off and schedules for every employee
- A client list with import, notes and automatic matching to client accounts
- Booking requests you approve or decline, or instant booking when you prefer
- Appointment history, completion and no-show tracking

**For clients**

- Passwordless sign-in with passkeys, with the email verified by a one-time code
- Booking, rescheduling and cancelling appointments
- Push and email notifications, with per-channel settings
- Notifications in the language of the client's device

**For operators**

- Runs as a single server process backed by MariaDB and Valkey (or Redis)
- Database migrations run automatically at startup
- Personal-data tools for GDPR: data export, account and client erasure, dormant account clean-up and retention rules (see [Privacy and GDPR](#privacy-and-gdpr))
- Draft privacy policy and terms of service templates for self-hosting businesses in English, Polish and Ukrainian (`docs/legal/template`)
- Prometheus metrics and structured logs
- An OpenAPI description of the whole API, served with Swagger UI

---

## Try it locally

You need **Docker** with Compose v2, **JDK 21** and about 4 GB of free memory.

```sh
git clone https://github.com/nocturneproperties/bookkme.git
cd bookkme/deployment/dev
./rollout-monolith.sh
```

The script starts MariaDB, Valkey and nginx, builds the server image and starts it. When it finishes:

- the API answers on `http://localhost:8085/api/`
- Swagger UI with every route is at `http://localhost:8085/api/monolith/internal/swagger`

The development setup does not send email. It writes every message to the server log instead, verification codes included, so you can sign up without a mail provider. Push notifications need your own Firebase key in `deployment/dev/security/firebase-private-key.json`.

To stop everything:

```sh
docker compose -f monolith-compose.yml down
docker compose -f environment-compose.yml down
```

---

## Self-hosting for your business

The [self-hosting permission](LICENSE-SELF-HOSTING.md) lets you run BookkMe to take bookings for your own business, with your staff and your clients using it, and lets a hosting provider or IT contractor run it for you. It does not let you offer BookkMe to other businesses.

### What you need

- A Linux server with Docker
- **MariaDB 11.8** or newer
- **Valkey 8** or a compatible Redis
- A domain with TLS, and a reverse proxy in front of the server (an nginx example is in `deployment/dev/nginx-monolith.conf`)
- A [Postmark](https://postmarkapp.com) account with a verified sending domain, for verification codes and email notifications
- A Firebase project, for push notifications

### Steps

1. **Build the image.** `./gradlew publishImageToLocalRegistry` builds `bookkme-server` into your local Docker. Release images are also published as `ghcr.io/nocturneproperties/bookkme-server:<version>`.
2. **Create the databases.** The server keeps its data in five schemas (`authorization`, `user`, `business`, `appointments`, `notifications`) and needs one user with full rights on all of them. `deployment/dev/db/init.sql` shows the statements. The tables are created on first start.
3. **Configure it.** Set the [environment variables](#configuration) and mount the two secret files.
4. **Start it** behind your reverse proxy, on HTTPS. Passkeys only work on the domain in `APPLICATION_DOMAIN_NAME`.
5. **Publish your legal texts.** Fill in the templates in `docs/legal/template` for your business, have them reviewed, and publish them as described in [`docs/legal-documents.md`](docs/legal-documents.md).

`deployment/prod` and `deployment/ansible` contain the production setup we run ourselves on Docker Swarm, with backups, monitoring and a VPN. Use them as a reference; they are written for our own infrastructure and will need adapting.

---

## Configuration

The server is configured through environment variables. `deployment/dev/service/monolith.env` is a working example for local use; never reuse its passwords and secrets in production.

| Variable | Purpose |
|---|---|
| `APPLICATION_DOMAIN_NAME` | Your public domain, used for passkeys and links |
| `APPLICATION_SERVICE_NAME` | `monolith` |
| `APPLICATION_SERVICE_HOSTNAME`, `APPLICATION_SERVICE_PORT` | Address the server binds to |
| `APPLICATION_SERVICE_VERSION` | Version shown in the API description |
| `APPLICATION_LOG_LEVEL` | `info`, `debug`, … |
| `APPLICATION_DB_URL`, `APPLICATION_DB_PORT`, `APPLICATION_DB_USER`, `APPLICATION_DB_PASSWORD` | Database connection |
| `APPLICATION_REDIS_HOSTS`, `APPLICATION_REDIS_PORT`, `APPLICATION_REDIS_PASSWORD` | Cache connection |
| `APPLICATION_AUTH_SERVICE_HOSTNAME`, `…_USER_…`, `…_BUSINESS_…`, `…_APPOINTMENTS_…`, `…_NOTIFICATIONS_…` | Internal addresses; in a single-server setup all point at the server itself |
| `APPLICATION_POSTMARK_SERVER_TOKEN` | Postmark server token; `development_token` logs emails instead of sending them |
| `APPLICATION_EMAIL_FROM` | Sender address, on your verified Postmark domain |
| `APPLICATION_EMAIL_CODE_SECRET` | Random secret of at least 32 bytes for verification codes |

Two secrets are read from files, as Docker secrets:

| Secret | Purpose |
|---|---|
| `signing_key_encryption_key` | Encrypts the keys the server signs access tokens with |
| `firebase_key` | Firebase service account key, for push notifications |

---

## Running as microservices

BookkMe is made of five services:

| Service | Responsibility |
|---|---|
| `auth` | Accounts, sign-up and email changes with email verification, passkey sign-in, access tokens, devices |
| `user` | Profiles, legal document acceptance, data export |
| `business` | Businesses, services, staff and invitations, permissions, clients, price quotes |
| `appointments` | Booking requests, approvals, appointments, schedules and history |
| `notifications` | Push and email notifications, device tokens, notification settings |

The same code runs in two ways:

- **As one server** (the default above). `MonolithServer` starts all five services in one process, and they pass events to each other in memory. You only need MariaDB and Valkey. This is what we run in production, and it is the right choice for a single business.
- **As five separate services.** Each service has its own entry point, its own image and its own database user that can only reach its own schema. The services send events to each other through **Apache Kafka** and call each other over internal HTTP. Choose this when you need to scale or deploy the services independently.

### Try it locally

```sh
cd deployment/dev
./rollout.sh
```

The script starts MariaDB, Valkey, Kafka (with Kafka UI on `http://localhost:8080`) and nginx, builds an image for each service and starts them. nginx on `http://localhost` routes `/api/auth`, `/api/user`, `/api/business`, `/api/appointments` and `/api/notifications` to the matching service, and each service serves its own Swagger UI, for example `http://localhost/api/appointments/internal/swagger`. Ports `8000`–`8004` are Java debugger ports, one per service, so you can attach your IDE.

### Configuration

Each service takes the variables from [Configuration](#configuration), with its own values; `deployment/dev/service/<service>.env` has an example for each. The differences from the single server:

| Variable | Purpose |
|---|---|
| `APPLICATION_SERVICE_NAME` | `auth`, `user`, `business`, `appointments` or `notifications`; it also sets the route prefix `/api/<name>` |
| `APPLICATION_DB_USER`, `APPLICATION_DB_PASSWORD` | The service's own database user |
| `APPLICATION_KAFKA_HOSTS` | Kafka bootstrap servers, separated by commas |
| `APPLICATION_KAFKA_CLUSTER_ID` | Kafka cluster id |
| `APPLICATION_<SERVICE>_SERVICE_HOSTNAME` | Where to reach the other services it calls |

Mount `signing_key_encryption_key` into `auth` and `business`, which sign tokens, and `firebase_key` into `notifications`. `auth` and `notifications` also need the email settings.

### Images

`./gradlew :service:<service>:microservice:publishImageToLocalRegistry` builds one service's image (`<service>` is `authorization`, `user`, `business`, `appointments` or `notifications`). Release images are published as `ghcr.io/nocturneproperties/bookkme-<service>:<version>`. `deployment/prod/microservice` and `deployment/prod/kafka` show a Docker Swarm setup for this mode.

---

## How the services communicate

Each service owns its data and never reads another service's database. They work together in two ways:

- **Events** for anything that happened. A service announces a change, such as `appointment.request_created`, `business.client_deleted` or `auth.user_deleted`, and every service that cares reacts to it in its own time. Notifications, for example, reacts to booking requests, approvals and cancellations by sending pushes and emails, and every service deletes its own data when it receives `auth.user_deleted`.
- **Internal HTTP calls** for data a service needs right away, such as the booking flow asking `business` for a service's price and duration.

Events are reliable:

- **Transactional outbox.** A service saves an event in its own `outbox_event` table in the same database transaction as the change it describes. A background relay publishes saved events afterwards. A failed change never produces an event, and an event is not lost when Kafka is down; it waits in the outbox.
- **At-least-once delivery, handled once.** An event can arrive more than once, after a crash or a restart, so each consumer records which events it has handled in Valkey and skips the repeats.
- **Retries and parking.** A failed handler is retried through a retry topic of its own. After the last attempt the event is parked in an `exhausted_event` table and raises a metric for alerts. Parked events are replayed on the next start, or when an operator asks for it, and are deleted after 30 days.

The single server uses exactly the same producers, outbox and consumers, with an in-memory transport in place of Kafka. Events are encoded as ProtoBuf and described by the `client` module of the service that publishes them. [`docs/transactional-outbox.md`](docs/transactional-outbox.md) and [`docs/exhausted-events.md`](docs/exhausted-events.md) go into detail.

---

## Security

**Sign-in**

- **No passwords.** Accounts sign in with passkeys (WebAuthn), so there is no password to steal, reuse or phish.
- **Verified email.** An account is created only after its owner enters a 6-digit code sent to the address, and the address changes only through a code sent to the new one. Codes expire after 10 minutes, allow a limited number of attempts and resends, and are stored only as keyed hashes.
- **Short-lived tokens.** Access tokens live 5 minutes. Refresh tokens belong to one device and are replaced on every use.
- **Rotating signing keys.** Token signing keys rotate daily and are published as a JWKS, so old keys stop being trusted on their own. The private keys are stored encrypted. Every token type has its own audience, so one kind of token cannot be used as another.
- **Confirmation for sensitive actions.** Deleting an account, deleting a client and exporting personal data each need a fresh passkey confirmation, not only a signed-in session.

**Access control**

- **Fine-grained permissions.** The owner gives each staff member separate view, edit and delete rights per area, and can suspend a staff member's access at once. Every request is checked on the server.
- **No probing.** A resource the caller may not see answers 404, the same as one that does not exist, so its existence is not revealed.
- **Business boundaries.** Every write is limited to the business the caller's permission was checked against, so an id from another business does nothing.
- **Server-side data.** Prices, durations, names and contact details are looked up on the server from their ids, never taken from what the app sends.

**Abuse and replay protection**

- **Idempotency keys** on requests, so a retried request never takes effect twice.
- **Single-use tokens** that are claimed atomically, so two concurrent requests cannot both use one.
- **Rate limits** in the nginx configuration for sending and checking email codes and for passkey sign-in.

**Infrastructure** (the production setup in `deployment/`)

- The database refuses unencrypted connections, and the server verifies its certificate.
- Database tables, logs and temporary files are encrypted at rest.
- The database and cache are reachable only from a private network, not from the reverse proxy or the internet, and traffic between hosts is encrypted.
- Backups are encrypted with a public key before they leave the server, and kept for 30 days.
- Secrets are passed as Docker secrets from an encrypted Ansible vault, and nothing from the vault stays on the server's disk.

Found a vulnerability? See [Support](#support) for how to report it privately.

---

## Privacy and GDPR

BookkMe is built to help whoever runs it meet the GDPR. It handles personal data with these tools:

- **Right of access and portability.** Users can export all their personal data from every service, after confirming with their passkey.
- **Right to erasure.** Deleting an account erases the user's data in every service, cancels their upcoming appointments and removes their personal details from the appointment history. A business can delete any of its clients in the same way.
- **Storage limitation.** An account unused for 3 years is deleted after three email warnings. Each business sets how long it keeps inactive clients (12 to 120 months, 36 by default), and older ones are deleted automatically. Booking requests are deleted 60 days after their time slot, and failed events after 30 days.
- **Erasure survives backups.** Every erasure is recorded, and restoring an older backup runs the erasures made since then again, so a deleted person does not come back.
- **Recorded acceptance.** The server records which version of the privacy policy and terms each user accepted.
- **Data minimisation.** Staff see only what their permissions allow, and notification emails go only to verified addresses.
- **Policy templates.** `docs/legal/template` contains a draft privacy policy and terms of service for a business running its own copy, in English, Polish and Ukrainian.

The software alone does not make a deployment compliant. When you self-host BookkMe, your business is the data controller: you decide the legal basis, publish your own privacy policy and terms (the templates are drafts that need review for your country), sign data processing agreements with your providers (such as your hosting provider, Postmark and Firebase), and answer requests from the people whose data you hold. [`docs/legal/README.md`](docs/legal/README.md) lists what to complete before publishing.

---

## Upgrading

Every release is described in [`RELEASE_NOTES.md`](RELEASE_NOTES.md): what changed for apps and operators, new settings, and anything that can stop an upgrade. Read the entries between your version and the new one before upgrading, and back up the database. Migrations run when the new version starts.

Versions follow semantic versioning. While the version is `0.x`, a minor release can contain breaking changes.

---

## Building from source

```sh
./gradlew build                                   # compile and test everything
./gradlew test                                    # run the tests
./gradlew :service:appointments:domain:impl:test  # test one module
./gradlew spotlessApply                           # format the code
```

Some tests start MariaDB, Valkey and Kafka with Testcontainers, so `./gradlew test` needs Docker running.

The server is written in Kotlin with Ktor, Koin, Exposed and Flyway, and speaks ProtoBuf (`application/x-protobuf`) rather than JSON.

---

## Project layout

```
src/            the single-server entry point that runs every service in one process
service/        authorization, user, business, appointments, notifications
core/           shared server, database, event and cache code
library/        money, permissions, validation, email, scheduling and other building blocks
build-src/      Gradle convention plugins
deployment/     local development stack and our production setup
docs/           database diagrams, operation diagrams, legal texts and design notes
```

Each service can also run as its own process, talking to the others over Kafka (see [Running as microservices](#running-as-microservices)). [`AGENTS.md`](AGENTS.md) and `docs/agents/` describe the code conventions in detail.

---

## Support

BookkMe is maintained by a small team, and support is best effort. [GitHub issues](https://github.com/nocturneproperties/bookkme/issues) are for bugs only: include the version you run and the steps that reproduce the problem. Please do not report security vulnerabilities in a public issue; use GitHub's private vulnerability reporting for this repository instead.

---

## Feature requests

Suggest new features and improvements in [GitHub Discussions](https://github.com/nocturneproperties/bookkme/discussions), not in issues. Describe the problem you want solved, not only the solution, so others can add their use cases and vote on it. **Feature requests opened as issues will be closed.**

---

## Contributing

**Unfortunately, we do not accept contributions.** Pull requests will be closed without review. This keeps the code, its licensing and its legal texts under one owner.

You are welcome to fork the project and change your copy within the terms of the license. Bug reports in issues and ideas in Discussions are always appreciated.

---

## License

BookkMe is **source-available** under the **Functional Source License, Version 1.1, ALv2 Future License** (`FSL-1.1-ALv2`) — see [`LICENSE`](LICENSE) — plus an [additional permission for self-hosting](LICENSE-SELF-HOSTING.md).

- **Allowed:** reading, modifying and running the code for any purpose other than a competing product — internal use, non-commercial education and research, and self-hosting BookkMe to take bookings for your own business, with your own clients booking through it.
- **Not allowed:** offering BookkMe, or a product built from it, to other businesses — as a hosted service, as software you sell, or as part of a booking platform.
- **Becomes Apache 2.0 after two years:** each version is also licensed under the Apache License, Version 2.0 from the second anniversary of its release.
- **Earlier versions stay Apache 2.0:** versions published before the switch to FSL — every commit whose `LICENSE` file is the Apache License — were released under the Apache License, Version 2.0 and remain available under it.

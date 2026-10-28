# Scheduled (recurring) jobs

Background operations that run on a fixed interval instead of being
triggered by an HTTP request or a Kafka event. Each microservice that has
recurring work exposes an `Application.register<Svc>Jobs(scheduler: SchedulerConfiguration)`
function in its `<Svc>Microservice.kt` that registers one or more
`job(name, interval) { ... }` blocks, resolving each operation from the
service's Koin scope. The standalone `main()` calls it inside
`install(Scheduler) { ... }`; `MonolithServer` installs a single
`Scheduler` whose `registerMonolithJobs` calls every service's
`register<Svc>Jobs` into the same configuration, so job
names must be unique across all services, not just within one. Every job
name therefore starts with its service's name constant and a dash
(`"$AUTH_SERVICE_NAME-rotateSigningKeys"` → `auth-rotateSigningKeys`, next to
`business-rotateSigningKeys`), which `MonolithJobsTest` enforces.

## How the scheduler works

- `Scheduler` (`library/scheduler/src/main/kotlin/library/scheduler/SchedulerPlugin.kt`) is a Ktor
  application plugin. It starts a `JobRunner` on `ApplicationStarted` and cancels it on
  `ApplicationStopping`.
- Each registered job runs on its own coroutine (`Dispatchers.Default` by default), waits
  `initialDelay` (zero unless specified), then loops: run the job's `action`, wait `interval`,
  repeat.
- A job that throws is caught and logged (`Scheduled job '<name>' failed`) by the runner; the
  loop is **not** broken — the job simply retries on its next tick after `interval`.
- Job names must be unique per `Scheduler` installation (enforced at registration).
- **No distributed lock.** If a microservice runs with more than one replica, every replica runs
  every job on its own schedule — jobs must be safe to run concurrently/redundantly (all current
  jobs are idempotent deletes/updates keyed on a time cutoff, so redundant runs are harmless).
- Operations invoked by a job still go through `transactionManager.transaction { }` like any other
  operation, so a mid-job failure rolls back cleanly; see the invoked operation's own error cases
  for what "failure" can mean (in practice: infra errors only — none of the jobs below have
  business error cases, since they take no caller input to validate).

## Appointments service

Registered in `registerAppointmentsJobs` (`AppointmentsMicroservice.kt`).

| Job | Interval | Operation | What it does |
|---|---|---|---|
| `appointments-markAppointmentsAsCompleted` | 5 minutes | `MarkAppointmentsCompleted` | `AppointmentDataSource.markCompleted(now)` — marks every `SCHEDULED` appointment with `dateEnd < now` as completed with `completedBy = SYSTEM`, but only for businesses whose `appointment_settings.automatic_completion` is on. Businesses with it off complete appointments themselves via [Complete appointment](appointments/complete-appointment.md) (`completedBy = USER`). |
| `appointments-deleteOutdatedRequests` | 1 hour | `DeleteOutdatedRequests` | `AppointmentRequestDataSource.cancelOutdated(now)` — cancels pending appointment requests whose slot has passed (`dateEnd < now`). |

## Authorization service

Registered in `registerAuthJobs` (`AuthMicroservice.kt`).

| Job | Interval | Operation | What it does |
|---|---|---|---|
| `auth-rotateSigningKeys` | 1 day | `RotateSigningKeys` (`retireInterval = 7.days`) | Generates a new RSA key pair and inserts it as the active signing key, marks the previously active key `RETIRING`, then deletes any key that has been retired for more than `retireInterval`. |
| `auth-deleteInactiveDevices` | 1 day | `DeleteInactiveDevices` | `DeviceDataSource.deleteInactiveDevices(olderThan = now - 30.days)`, then sends `AuthEvent.DeviceDeleted` for every deleted device. |

## Business service

Registered in `registerBusinessJobs` (`BusinessMicroservice.kt`).

| Job | Interval | Operation | What it does |
|---|---|---|---|
| `business-rotateSigningKeys` | 7 days | `RotateSigningKeys` (`retireInterval = 7.days`) | Generates a new RSA key pair and inserts it as the active signing key, marks the previously active key `RETIRING`, then deletes any key that has been retired for more than `retireInterval`. |
| `business-deleteDayOffsInThePast` | 1 day | `DeleteDayOffsInThePast` | `BusinessDataSource.deleteDayOffsInThePast()` — removes day-off ranges whose end date has already passed. |
| `business-expireEmployeeInvitations` | 1 day | `ExpireEmployeeInvitations` | `EmployeeInvitationDataSource.expireOldInvitations(now - 7.days)` — marks every still-`PENDING` invitation older than 7 days as `EXPIRED` and clears its `code_hash` column so the code can be reused. |
| `business-deleteProcessedEmployeeInvitations` | 1 day | `DeleteProcessedEmployeeInvitations` | `EmployeeInvitationDataSource.deleteProcessedInvitations(now - 30.days)` — hard-deletes every non-`PENDING` (redeemed, revoked, expired) invitation whose `updatedAt` is older than 30 days (or, for legacy rows with no `updatedAt`, whose `createdAt` is). The 30-day retention must stay longer than the 24-hour daily invitation quota window of [Invite employee](business/create-employee-invitation.md), since the quota counts these rows. |

## Adding a new job

1. Write the operation like any other (`domain/api` interface + `domain/impl` impl +
   `transactionManager.transaction { }`), registered in the service's `di/DI.kt`. Recurring jobs
   typically take no parameters and return `Result<Unit>`.
2. Register it in the service's `register<Svc>Jobs` with
   `scheduler.job("$<SVC>_SERVICE_NAME-<jobName>", interval) { scope.get<Op>().invoke().getOrThrow() }` — without
   `getOrThrow()` a failed `Result` is silently treated as success and never logged. If the
   service has no `register<Svc>Jobs` yet, add one, call it from `main()` inside
   `install(Scheduler) { }`, and add it to `registerMonolithJobs` in `MonolithServer.kt` — plus
   to the expected union and the prefix check in `MonolithJobsTest` (root project), which fail on
   a cross-service job-name clash, a service missing from the monolith, or a job name without its
   service prefix (the root project needs the service's `domain.api` as a `testImplementation`
   for the name constant).
3. Cover the wiring in the service's `<Svc>JobsTest` (microservice module, e.g. `BusinessJobsTest`):
   boot a scope of mocked operations with `startScopedApplication(<Svc>Scope) { scoped { op } }`
   (`testFixtures(projects.core.service)`), call `register<Svc>Jobs(SchedulerConfiguration())`, then
   use `testFixtures(projects.library.scheduler)`'s `registeredJobs()` (name → interval map, assert
   the whole map so a forgotten job fails) and `runJob(name)` (runs the action once, no timing).
   Per job: one test verifying the operation is invoked with the right arguments, one asserting a
   `Result.failure` propagates out of `runJob`. Timing/retry behavior itself is already covered by
   `library/scheduler`'s own `JobRunnerTest` — don't re-test it per service.
4. Add a row to this file's table for that service (create the service's section if it's the
   first scheduled job there).

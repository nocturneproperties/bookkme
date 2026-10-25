# Operation activity diagrams

One Mermaid activity diagram per HTTP route that mutates state (every
`POST`/`PUT`/`PATCH`/`DELETE` route backed by a business operation), plus
one per asynchronous reaction to a cross-service Kafka event, grouped by
microservice. Each route's KDoc carries a `See:` line pointing at its file,
so the diagram is reachable both from here and from the route handler in
the code. Every diagram whose operation sends a cross-service event ends
with a **Consumed by** line linking forward to the diagram(s) that react to
it; every reaction diagram links back to the producer(s) that fire it, so
following either direction from any file traces the whole cascade.

Read-only (`GET`) routes and routes with no persisted side effect (e.g.
issuing a signed quote token) are out of scope — see [Database ER
diagrams](../database/README.md) for the schema those mutations write
to. Background operations that run on a timer instead of a route or event
are documented separately in [Scheduled (recurring)
jobs](scheduled-jobs.md).

- [Appointments service](#appointments-service)
- [Authorization service](#authorization-service)
- [Business service](#business-service)
- [Notifications service](#notifications-service)
- [User service](#user-service)
- [Cross-service event map](#cross-service-event-map)
- [Scheduled (recurring) jobs](scheduled-jobs.md)

## Appointments service

| Route | Diagram |
|---|---|
| `POST /api/appointments/enabled/{businessId}` | [Enable appointments for business](appointments/enable-appointments-for-business.md) |
| `POST /api/appointments/request` | [Create appointment request](appointments/create-appointment-request.md) |
| `POST /api/appointments/request/{id}/decline` | [Decline appointment request](appointments/decline-appointment-request.md) |
| `PUT /api/appointments/{id}` | [Update appointment (reschedule)](appointments/update-appointment.md) |
| `POST /api/appointments` | [Create appointment from a pending request](appointments/create-appointment-from-request.md) |
| `POST /api/appointments/instant` | [Create instant appointment](appointments/create-appointment-instant.md) |
| `POST /api/appointments/{id}/cancel` | [Cancel appointment](appointments/cancel-appointment.md) |
| `POST /api/appointments/{id}/no-show` | [Mark appointment as no-show](appointments/mark-appointment-no-show.md) |
| `POST /api/appointments/{id}/complete` | [Complete appointment](appointments/complete-appointment.md) |
| `PUT /api/appointments/settings/{businessId}` | [Edit appointment settings](appointments/edit-appointment-settings.md) |

Reactions to cross-service events:

| Event | Diagram |
|---|---|
| `BusinessEvent.Deleted` | [React to business deletion](appointments/on-business-deleted.md) |
| `BusinessEvent.Updated` | [React to business update](appointments/on-business-updated.md) |
| `AuthEvent.UserDeleted` | [React to user deletion](appointments/on-user-deleted.md) |
| `BusinessEvent.EmployeePermissionsChanged` | [React to an employee permissions change](appointments/on-employee-permissions-changed.md) |

## Authorization service

| Route | Diagram |
|---|---|
| `POST /api/auth/sign_up/challenge` | [Get sign-up challenge](authorization/sign-up-challenge.md) |
| `POST /api/auth/sign_up` | [Verify sign-up (create account)](authorization/sign-up.md) |
| `POST /api/auth/sign_in` | [Verify sign-in](authorization/sign-in.md) |
| `POST /api/auth/refresh` | [Refresh access token](authorization/refresh-token.md) |
| `DELETE /api/auth/session` | [Sign out](authorization/sign-out.md) |
| `DELETE /api/auth/account` | [Delete account](authorization/delete-account.md) |
| `POST /api/auth/passkey/add/finish` | [Add passkey to account](authorization/add-passkey.md) |
| `DELETE /api/auth/passkey/{id}` | [Delete passkey](authorization/delete-passkey.md) |

## Business service

| Route | Diagram |
|---|---|
| `POST /api/business` | [Create business](business/create-business.md) |
| `PUT /api/business/{id}` | [Update business](business/update-business.md) |
| `PUT /api/business/{id}/dashboard` | [Set dashboard business](business/set-dashboard-business.md) |
| `POST /api/business/{businessId}/clients` | [Create client](business/create-client.md) |
| `DELETE /api/business/{businessId}/clients/{id}` | [Delete client](business/delete-client.md) |
| `PUT /api/business/{businessId}/clients/{id}` | [Update client](business/update-client.md) |
| `PUT /api/business/{businessId}/employee/{id}` | [Update employee](business/update-employee.md) |
| `PUT /api/business/{businessId}/employee/{id}/permissions` | [Set employee permissions](business/set-employee-permissions.md) |
| `PUT /api/business/{businessId}/employee/{id}/suspension` | [Set employee suspension](business/set-employee-suspension.md) |
| `POST /api/business/{businessId}/employee_invitation` | [Invite employee](business/create-employee-invitation.md) |
| `POST /api/business/employee_invitation/redeem` | [Join business](business/join-business.md) |
| `POST /api/business/{businessId}/employee_invitation/{id}/revoke` | [Revoke employee invitation](business/revoke-employee-invitation.md) |
| `POST /api/business/{businessId}/service` | [Create service](business/create-service.md) |
| `PUT /api/business/{businessId}/service/{id}` | [Update service](business/update-service.md) |
| `DELETE /api/business/{businessId}/service/{id}` | [Delete service](business/delete-service.md) |
| `POST /api/business/{businessId}/service_group` | [Create service group](business/create-service-group.md) |
| `DELETE /api/business/{businessId}/service_group/{id}` | [Delete service group](business/delete-service-group.md) |

Reactions to cross-service events:

| Event | Diagram |
|---|---|
| `AuthEvent.UserDeleted` | [React to user deletion](business/on-user-deleted.md) |
| `UserEvent.Updated` | [React to user profile update](business/on-user-updated.md) |

## Notifications service

| Route | Diagram |
|---|---|
| `PUT /api/notification/settings` | [Update notification settings](notifications/update-notification-settings.md) |
| `PUT /api/notification/token/{deviceUuid}` | [Update push notification token](notifications/update-push-token.md) |

Reactions to cross-service events (all handled by the same
`NotificationEventHandler`):

| Event | Diagram |
|---|---|
| `AuthEvent.DeviceCreated` | [React to device creation](notifications/on-device-created.md) |
| `AuthEvent.UserDeleted` | [React to user deletion](notifications/on-user-deleted.md) |
| `AuthEvent.DeviceLanguageUpdated` | [React to device language change](notifications/on-device-language-updated.md) |
| `AuthEvent.DeviceDeleted` | [React to device deletion](notifications/on-device-deleted.md) |
| `UserEvent.Updated` | [React to user profile update](notifications/on-user-updated.md) |
| `AppointmentEvent.RequestCreated` | [React to appointment request creation](notifications/on-appointment-request-created.md) |
| `AppointmentEvent.RequestApproved` | [React to appointment request approval](notifications/on-appointment-request-approved.md) |
| `AppointmentEvent.RequestRejected` | [React to appointment request decline](notifications/on-appointment-request-rejected.md) |
| `AppointmentEvent.Cancelled` | [React to appointment cancellation](notifications/on-appointment-cancelled.md) |
| `BusinessEvent.EmployeeInvitationRedeemed` | [React to an employee joining the business](notifications/on-employee-invitation-redeemed.md) |

## User service

| Route | Diagram |
|---|---|
| `PATCH /api/user/me` | [Update user](user/update-user.md) |
| `POST /api/user/contact_us` | [Send contact form](user/create-contact-form.md) |
| `POST /api/internal/user` | [Create user (internal)](user/create-user-internal.md) |

Reactions to cross-service events:

| Event | Diagram |
|---|---|
| `AuthEvent.UserDeleted` | [React to user deletion](user/on-user-deleted.md) |

## Cross-service event map

Every event that crosses a service boundary, who fires it, and who reacts.
`AuthEvent.UserDeleted` is the widest fan-out in the system — four services
react to a single account deletion, independently and in no guaranteed
order.

Business events are delivered through the transactional outbox: the
`eventProducer.send` node in each producer diagram stores the event in the
service's `outbox_event` table inside the operation's transaction, and
`OutboxRelay` publishes it after commit. An event therefore exists only if
the operation committed, and reaches its consumers at least once. See
[Transactional outbox](../transactional-outbox.md) for the full flow.

| Event | Producer | Consumer(s) |
|---|---|---|
| `AuthEvent.UserDeleted` | [Delete account](authorization/delete-account.md) | [appointments](appointments/on-user-deleted.md), [business](business/on-user-deleted.md), [notifications](notifications/on-user-deleted.md), [user](user/on-user-deleted.md) |
| `AuthEvent.DeviceDeleted` | [Delete account](authorization/delete-account.md) | [notifications](notifications/on-device-deleted.md) |
| `AuthEvent.DeviceCreated` | [Verify sign-up](authorization/sign-up.md), [Verify sign-in](authorization/sign-in.md) | [notifications](notifications/on-device-created.md) |
| `AuthEvent.DeviceLanguageUpdated` | [Verify sign-in](authorization/sign-in.md) | [notifications](notifications/on-device-language-updated.md) |
| `BusinessEvent.Updated` | [Update business](business/update-business.md) | [appointments](appointments/on-business-updated.md) |
| `BusinessEvent.Deleted` | [React to user deletion (business)](business/on-user-deleted.md) | [appointments](appointments/on-business-deleted.md) |
| `BusinessEvent.EmployeeInvitationRedeemed` | [Join business](business/join-business.md) | [notifications](notifications/on-employee-invitation-redeemed.md) |
| `BusinessEvent.EmployeePermissionsChanged` | [Join business](business/join-business.md), [Set employee permissions](business/set-employee-permissions.md), [Set employee suspension](business/set-employee-suspension.md) | [appointments](appointments/on-employee-permissions-changed.md) |
| `UserEvent.Updated` | [Update user](user/update-user.md) | [business](business/on-user-updated.md), [notifications](notifications/on-user-updated.md) |
| `AppointmentEvent.RequestCreated` | [Create appointment request](appointments/create-appointment-request.md) | [notifications](notifications/on-appointment-request-created.md) |
| `AppointmentEvent.RequestApproved` | [Create appointment from a pending request](appointments/create-appointment-from-request.md), [Create appointment request](appointments/create-appointment-request.md) (auto-approval) | [notifications](notifications/on-appointment-request-approved.md) |
| `AppointmentEvent.RequestRejected` | [Decline appointment request](appointments/decline-appointment-request.md) | [notifications](notifications/on-appointment-request-rejected.md) |
| `AppointmentEvent.Cancelled` | [Cancel appointment](appointments/cancel-appointment.md) | [notifications](notifications/on-appointment-cancelled.md) |

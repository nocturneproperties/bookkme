# Push notifications

Pushes go out through Firebase Cloud Messaging. A visible push has a title and
a body. A push the app needs to find again later, for example to remove it once
it is out of date, also carries a subject: a key and some data. A data-only
push shows nothing and only tells the app something changed. The server and the
apps both follow this page, so a tag format, data key or `type` value changes
here first, together with the apps.

## Fields set for a push with a subject

| Field | Value |
|---|---|
| Android notification tag (`android.notification.tag`) | The subject key |
| APNs header `apns-collapse-id` | The subject key, at most 64 bytes |
| FCM data | The subject data |

On Android the app finds the shown notification by its tag. On iOS the
collapse id becomes the delivered notification's identifier. A second push with
the same key replaces the first on both. A push without a subject has none of
these fields.

## Subjects

| Push | Key | Data |
|---|---|---|
| New appointment request, to the assigned employee | `appointment_request:<requestId>` | `appointmentRequestId` = `<requestId>` |

`<requestId>` is the request's id in its standard 36-character form, the same
id the appointment request routes return. A request-created event published
by a server older than 0.21.0 sends the push without a subject.

Every other push (approved, rejected and cancelled appointments, account
notices) has no subject.

## Data-only pushes

A data-only push has no title or body and is never shown. It goes to every
device of the user that has a push token, and never by email or Telegram. It
follows the same notification settings as the shown push it belongs to: it is
not sent when the user turned off push notifications or that kind of
notification (appointment notifications for `appointment_request_resolved`),
since no shown push was sent either.

| Field | Value |
|---|---|
| `notification` | Not set |
| FCM data `type` | What happened, from the table below |
| Android priority (`android.priority`) | `normal` |
| APNs `aps` | `content-available` = 1, no `alert`, `sound` or `badge` |
| APNs headers | `apns-push-type: background`, `apns-priority: 5` |

| `type` | Sent to | Other data | When |
|---|---|---|---|
| `appointment_request_resolved` | The request's assigned employee | `appointmentRequestId` = `<requestId>` | The request stopped being pending: approved, declined, withdrawn by the client, cancelled once its slot passed, or deleted with its client or the client's account. Not sent when the deleted account is the employee's own |

On receiving it, the app removes the shown push whose Android tag or APNs
collapse id is `appointment_request:<requestId>`. Delivery is not guaranteed:
Android may delay a normal-priority message, and iOS may delay or drop a
background push. The app ignores a `type` it does not know.

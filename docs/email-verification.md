# Email verification

Every email address Bookk stores was proven by a 6-digit code sent to it.
Sign-up redeems a code before the account exists, and the address changes
afterwards only through a code sent to the new one, so nothing downstream
checks whether an address is verified. A business client is linked to an
account by that email alone (`docs/agents/domain-rules.md`). Scope and
decisions: issues #146 and #147.

## Codes (`library/email`)

`emailModule(Scope)` binds the code machinery and the sender for a service;
only auth uses it today. The state lives in the cache (Valkey in prod),
never in a database.

| Limit | Value | Error |
|---|---|---|
| Code length | 6 digits | |
| Code lifetime | 10 minutes | `EMAIL_CODE_EXPIRED` (910002) |
| Resend cooldown, per address | 60 seconds | 429 `EMAIL_CODE_RESEND_TOO_SOON` (910004) |
| Sends per address | 5 an hour | 429 `EMAIL_CODE_RESEND_TOO_SOON` (910004) |
| Wrong codes per verification | 5; the sixth try ends it | `EMAIL_CODE_ATTEMPTS_EXCEEDED` (910003) |
| Postmark refuses the address | | `EMAIL_UNDELIVERABLE` (910005) |
| Wrong code | | `EMAIL_CODE_INVALID` (910001) |

All errors are 422 unless marked 429.

- **Issue** (`EmailCodeIssuer.issue(email, purpose, ownerId, render)`)
  claims the address's cooldown, counts the send, stores the session
  (owner, address, purpose, the code's HMAC) under a new `verificationId`,
  mails the code and only then makes it the owner's newest code. A failed
  send releases the cooldown, keeps the previous code working, and leaves
  the hourly count spent. It returns `EmailVerificationStarted`
  (`verificationId`, `expiresAt`, `resendAvailableAt`).
- **Redeem** (`EmailCodeRedeemer.redeem(verificationId, code, ownerId) { verified -> … }`)
  accepts only the owner's newest code, counts the attempt, compares the
  HMAC, claims a single-use `:redeemed` key and runs the block with
  `VerifiedEmail(email, purpose)`. If the block fails or is cancelled the key
  is released, so the same code can be tried again within its lifetime. Two
  concurrent redeems of the right code: the loser gets
  `EMAIL_CODE_EXPIRED`.
- **Secrets.** Codes and the per-address cache keys are HMAC-SHA256 under
  `APPLICATION_EMAIL_CODE_SECRET` (`EmailCodeHasher`), so a cache dump
  reveals neither codes nor addresses. Rotating the secret only invalidates
  codes still pending.

| Cache key | Holds | Lives |
|---|---|---|
| `email-verification:<verificationId>` | the session | 10 minutes |
| `email-verification:<verificationId>:attempts` | redeem attempts | 10 minutes |
| `email-verification:<verificationId>:redeemed` | single-use claim | 10 minutes |
| `email-verification:latest:<ownerId>` | the owner's newest `verificationId` | 10 minutes |
| `email-verification:cooldown:<address HMAC>` | resend cooldown | 60 seconds |
| `email-verification:sent:<address HMAC>` | sends this hour | 1 hour from the first |

## Sign-up

1. `POST /api/auth/passkey/sign_up/challenge` (`StartRegistration`) rejects
   a malformed or taken address, starts the passkey registration and issues
   a `sign-up` code owned by the challenge's `requestId`. The response
   carries both (`SignUpChallengeResponse.emailVerification`, field 6). The
   passkey challenge lives 10 minutes, as long as the code. Calling it
   again resends.
2. `POST /api/auth/sign_up` (`FinishRegistration`) redeems
   `verificationId` (6) and `emailCode` (7) first, so a mistyped code does
   not spend the passkey challenge, then verifies the passkey and creates
   the user with the redeemed address. `userInfo.email` is ignored.

Diagrams: [sign-up challenge](operations/authorization/sign-up-challenge.md),
[sign-up](operations/authorization/sign-up.md).

## Email change

1. `POST /api/auth/email` (`StartEmailChange`, `EmailChangeRequest`) rejects
   a malformed or taken address and issues an `email-change` code owned by
   the caller.
2. `POST /api/auth/email/confirm` (`ConfirmEmailChange`, `EmailConfirmation`)
   redeems it and calls user's internal `POST /api/internal/user/{id}/email`
   (`ChangeUserEmail`), the only writer of an email after sign-up. An
   address taken in the meantime is `EMAIL_EXIST` (1), and the code can be
   used again for another try.
3. User publishes `UserEvent.Updated`; business merges the user's clients by
   the new email ([on user updated](operations/business/on-user-updated.md)).

`PATCH /api/user/me` never changes the email: a different one is 422
`EMAIL_CHANGE_REQUIRES_VERIFICATION` (100006).

Diagrams: [start](operations/authorization/start-email-change.md),
[confirm](operations/authorization/confirm-email-change.md),
[internal write](operations/user/change-user-email-internal.md).

## Adding a new use

A new path that stores an address issues a code with its own purpose in
`EmailPurpose`, redeems it, checks `verified.purpose` inside the block and
does the write there. Never send an email inside
`transactionManager.transaction { }`. Test it against `EmailTestMailbox`
(`testFixtures(projects.library.email.impl)`), not a mocked issuer or
redeemer. The rules are in `docs/agents/security.md`.

## Operating it

- **Settings.** Auth (the monolith in prod) needs
  `APPLICATION_POSTMARK_SERVER_TOKEN`, `APPLICATION_EMAIL_FROM` and
  `APPLICATION_EMAIL_CODE_SECRET` (at least 32 bytes), or every sign-up and
  email change fails with 500. Prod reads them from `vault_postmark_server_token`,
  `no-reply@<EMAIL_DOMAIN_NAME>` (`vault_stack_env`, separate from the app's `DOMAIN_NAME`) and `vault_email_code_secret`.
- **Postmark.** Sent through its HTTP API on the `outbound` stream. A 422
  with Postmark error 300 (invalid address) or 406 (inactive recipient)
  becomes `EMAIL_UNDELIVERABLE`; 429, 5xx, timeouts and connection errors
  are retried twice, and any other refusal is a 500. The
  sending domain needs its DKIM and Return-Path records before the first
  deploy.
- **Dev.** The token `development_token` binds `LoggingEmailSender`: nothing
  is sent and each email, code included, is logged at INFO. The prod deploy
  refuses this token and `CHANGE_ME`.
- **Rate limits.** Prod nginx adds per-IP limits on code sends and checks on
  top of the per-address ones above (`deployment/AGENTS.md`); beyond them it
  answers 429 with an HTML body.
- **Emails** are rendered by `AuthEmails` from
  `i18n/auth_emails*.properties` in the `Accept-Language` language.

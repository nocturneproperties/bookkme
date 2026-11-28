# Confirm an action through Telegram

`POST /api/auth/telegram/confirm_action` → `ConfirmActionWithTelegram`

The second half of [Start confirming an action through
Telegram](telegram-confirm-action-start.md). The user taps the bot's button,
Telegram opens the Mini App with `start_param` = `op_<kind>_<challenge>`, and on
Confirm the Mini App sends the raw `Telegram.WebApp.initData`. `start_param` is
part of the launch data Telegram signs, so a challenge in it proves that this
Telegram account opened that link after the challenge was created. The answer
is the same 2-minute action confirmation token as [the passkey
route](action-confirmation.md), and everything after it
(`ActionConfirmationGate`, the protected operations) is unchanged.

The token's action, business id and target id come only from the stored
challenge, never from the request, so a confirmation started for an export
can't become an account deletion. The checks run in this order. The challenge
is read without being spent while it is checked, and taken atomically only once
every check passed, so a bad launch, another user, or a forwarded button opened
from another Telegram account doesn't spend it. The Telegram account check is
the counterpart of `FinishAssertion.finishOwnedBy`: without it, someone holding
a stolen access token could open the link in their own Telegram.

```mermaid
flowchart TD
    Start([POST /api/auth/telegram/confirm_action body TelegramActionConfirmationRequest]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Configured{"APPLICATION_TELEGRAM_BOT_ID set?"}
    Configured -- No --> R503([503 TELEGRAM_CONFIRMATION_UNAVAILABLE 25])
    Configured -- Yes --> Verify["TelegramInitDataVerifier.verifyLaunch initData, 5 minutes"]
    Verify -- bad Ed25519 signature, no user, auth_date over a minute ahead --> R422a([422 TELEGRAM_DATA_INVALID 14])
    Verify -- auth_date older than 5 minutes --> R422b([422 TELEGRAM_DATA_EXPIRED 15])
    Verify -- ok --> Parse{"start_param matches ^op_(delete-account|export-personal)_[0-9a-f]{32}$ ?"}
    Parse -- No, a link name, a UUID or another kind --> R422c([422 TELEGRAM_CONFIRMATION_EXPIRED 20])
    Parse -- Yes --> Read{"TelegramConfirmationDataSource.getChallenge - read, not spent"}
    Read -- missing, expired or already taken --> R422c
    Read -- found --> Owner{"challenge.userId = caller?"}
    Owner -- No --> R422c
    Owner -- Yes --> Kind{"start_param kind = challenge.action?"}
    Kind -- No --> R422c
    Kind -- Yes --> Account{"transaction - getAccountByTelegramUser initData user.id, by blind index - account.userId = caller?"}
    Account -- No or none --> R422d([422 TELEGRAM_ACCOUNT_MISMATCH 21])
    Account -- Yes --> Take{"takeChallenge - setIfAbsent marker, single use"}
    Take -- another request took it first --> R422c
    Take -- taken --> Issue["TokenIssuer.issueActionConfirmation 2 minutes - claims from the taken challenge"]
    Issue --> Publish["transaction - outbox: AuthEvent.TelegramActionConfirmed userId challengeId"]
    Publish -- committed or failed, failure only logged --> R200([200 ActionConfirmationToken])
```

**Consumed by:** `AuthEvent.TelegramActionConfirmed` → [notifications: mark the
bot's message confirmed](../notifications/on-telegram-action-confirmed.md). The
event only changes the chat message, so a failure to store it is logged and the
token is still returned.

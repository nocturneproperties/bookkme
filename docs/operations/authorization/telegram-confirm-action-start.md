# Start confirming an action through Telegram

`POST /api/auth/telegram/confirm_action/start` → `StartTelegramActionConfirmation`

The Mini App can't use a passkey (no WebAuthn in Telegram's in-app browser),
and an account created through Telegram has none, so it can't use [Confirm a
sensitive action with a passkey](action-confirmation.md). Instead, for any
account linked to Telegram, with or without a passkey,
the bot sends the user a one-time link to the Mini App, and the user confirms
from the Mini App that link opens ([Confirm an action through
Telegram](telegram-confirm-action.md)). Only the actions the Mini App offers
qualify: `DELETE_ACCOUNT` and `EXPORT_PERSONAL_DATA`, each with the nil business
id and the caller as target. Client deletion and the business data export stay
passkey-only.

The challenge is 128 random bits as 32 lowercase hex characters, kept in the
cache for 5 minutes with the user id, action, business id and target id
(`TelegramConfirmationDataSource`). The link's `start_param` is
`op_<kind>_<challenge>` ([launch parameters](../../telegram-launch-parameters.md)).
One start a minute per user is claimed with `setIfAbsent`; a failed send gives
the minute and the challenge back. The message goes out through notifications
(`SendTelegramActionConfirmation`, internal route, see [Send a Telegram action
confirmation link](../notifications/send-telegram-action-confirmation.md)),
which holds the Telegram chat id and the bot token; auth keeps only the blind
index of the Telegram user id. No transaction is open while it is sent, and
the call is never retried, so the user never gets the same message twice: a
failed Bot API call comes back as the outcome `FAILED`, not as an error.

```mermaid
flowchart TD
    Start([POST /api/auth/telegram/confirm_action/start body TelegramActionConfirmationStart]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Own{"action DELETE_ACCOUNT or EXPORT_PERSONAL_DATA, businessId nil, targetId = caller? - before any read"}
    Own -- No --> R422a([422 ACTION_NOT_CONFIRMABLE_THROUGH_TELEGRAM 24])
    Own -- Yes --> Configured{"APPLICATION_TELEGRAM_BOT_ID set? - TelegramInitDataVerifier.isConfigured"}
    Configured -- No --> R503([503 TELEGRAM_CONFIRMATION_UNAVAILABLE 25])
    Configured -- Yes --> Identity{"transaction - TelegramIdentityDataSource.hasIdentity caller"}
    Identity -- No --> R422b([422 TELEGRAM_IDENTITY_MISSING 19])
    Identity -- Yes --> Cooldown{"claimStartCooldown caller - setIfAbsent, 60 s"}
    Cooldown -- already claimed --> R429([429 TELEGRAM_CONFIRMATION_TOO_FREQUENT 23])
    Cooldown -- claimed --> Save["saveChallenge 32 hex - user, action, business, target, 5 minutes"]
    Save --> Send["notifications SendTelegramActionConfirmation - action, op_kind_challenge, Accept-Language, no retry"]
    Send -- SENT --> R204([204 No Content])
    Send -- other outcome or failure --> GiveBack["deleteChallenge, releaseStartCooldown"]
    GiveBack -- CHAT_UNREACHABLE --> R422c([422 TELEGRAM_CHAT_UNREACHABLE 22])
    GiveBack -- NO_CHAT, UNAVAILABLE or FAILED --> R503
    GiveBack -- notifications unreachable --> R500([500])
```

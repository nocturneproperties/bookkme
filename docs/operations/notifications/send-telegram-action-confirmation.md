# Send a Telegram action confirmation link

`POST /api/internal/telegram/action_confirmation/{userId}` → `SendTelegramActionConfirmation` (internal, called by auth's [Start confirming an action through Telegram](../authorization/telegram-confirm-action-start.md))

The bot writes to the user's private chat, in the language auth passes (the
start request's `Accept-Language`), which action they asked to confirm, with a
URL button that opens the Mini App with the given `start_param`
([launch parameters](../../telegram-launch-parameters.md)). Plain text, no
`parse_mode`. It is sent even when the user turned the Telegram notification
channel off, and a 403 leaves that setting alone: the user can allow the bot to
write (`requestWriteAccess`) and start again. The chat id is the stored
Telegram user id (`notification_telegram_targets`). Once the new message is
sent, the user's previous confirmation message that was never confirmed is
deleted from the chat (`deleteMessage`), so only the newest button is left; a
message that wasn't sent leaves the previous one in place, since its challenge
may still be usable. The new message id is kept in Valkey under the user
(`telegram-confirmation-message:<userId>`, 47 hours, just under Telegram's
48-hour limit for deleting a bot message) with the challenge id and the
"confirmed" text in the same language, for [marking it
confirmed](on-telegram-action-confirmed.md). Looking up, deleting and
remembering are best effort: a failure is logged and never changes the answer.
No database row is written. A Bot API failure, after the Bot API client's own retries, is logged and answered
as `FAILED`, never as a 500, and auth calls this route without retries, so a
slow send can't make the bot write twice.

```mermaid
flowchart TD
    Start([POST /api/internal/telegram/action_confirmation/userId body TelegramActionConfirmationMessage]) --> Link{"APPLICATION_TELEGRAM_BOT_USERNAME set?"}
    Link -- No --> Unavailable([200 UNAVAILABLE])
    Link -- Yes --> Chat{"transaction - NotificationTargetDataSource.getTelegram userId"}
    Chat -- none --> NoChat([200 NO_CHAT])
    Chat -- chat id --> Previous["TelegramConfirmationMessageDataSource.get userId - failure logged"]
    Previous --> Send["TelegramMessenger.send text, button t.me/bot?startapp=start_param"]
    Send -- sent --> Delete["TelegramMessenger.delete the previous message, if any - failure logged"]
    Delete --> Remember["save userId - challengeId, messageId, confirmed text, 47 hours; without a message id forget the previous one - failure logged"]
    Remember --> Sent([200 SENT])
    Send -- 403 --> Unreachable([200 CHAT_UNREACHABLE])
    Send -- no bot token, dropped --> Unavailable
    Send -- transport failure after retries --> Failed([200 FAILED])
```

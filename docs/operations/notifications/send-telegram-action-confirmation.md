# Send a Telegram action confirmation link

`POST /api/internal/telegram/action_confirmation/{userId}` → `SendTelegramActionConfirmation` (internal, called by auth's [Start confirming an action through Telegram](../authorization/telegram-confirm-action-start.md))

The bot writes to the user's private chat, in the language auth passes (the
start request's `Accept-Language`), which action they asked to confirm, with a
URL button that opens the Mini App with the given `start_param`
([launch parameters](../../telegram-launch-parameters.md)). Plain text, no
`parse_mode`. It is sent even when the user turned the Telegram notification
channel off, and a 403 leaves that setting alone: the user can allow the bot to
write (`requestWriteAccess`) and start again. The chat id is the stored
Telegram user id (`notification_telegram_targets`). Nothing is written. A
Bot API failure, after the Bot API client's own retries, is logged and answered
as `FAILED`, never as a 500, and auth calls this route without retries, so a
slow send can't make the bot write twice.

```mermaid
flowchart TD
    Start([POST /api/internal/telegram/action_confirmation/userId body TelegramActionConfirmationMessage]) --> Link{"APPLICATION_TELEGRAM_BOT_USERNAME set?"}
    Link -- No --> Unavailable([200 UNAVAILABLE])
    Link -- Yes --> Chat{"transaction - NotificationTargetDataSource.getTelegram userId"}
    Chat -- none --> NoChat([200 NO_CHAT])
    Chat -- chat id --> Send["TelegramMessenger.send text, button t.me/bot?startapp=start_param"]
    Send -- sent --> Sent([200 SENT])
    Send -- 403 --> Unreachable([200 CHAT_UNREACHABLE])
    Send -- no bot token, dropped --> Unavailable
    Send -- transport failure after retries --> Failed([200 FAILED])
```

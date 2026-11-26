# Telegram Mini App launch parameters

The Mini App is opened with `start_param` set from a `t.me` link's `startapp`
value. Telegram limits it to 512 characters of `A-Za-z0-9_-` and signs it with
the rest of the launch data. Both the server, which builds the links, and the
client, which parses them, follow this page. A new kind is added here first.

## Format

| `start_param` | Opens |
|---|---|
| `op_<kind>_<payload>` | A launch BookkMe creates for its own purposes. The kind is `[a-z]+(-[a-z]+)*` and the payload is whatever that kind defines |
| A UUID | That business (`bookkme.com/{id}`) |
| Anything else | The business with that link name (`bookkme.com/{name}`, `^[a-z0-9]+(-[a-z0-9]+)*$`) |

Public business links stay bare. The prefix `op_` can never be a link name,
because link names never contain `_` (`BusinessLinkPolicy`, pinned by
`BusinessLinkPolicyTest` and `docs/agents/domain-rules.md`).

## Routing in the Mini App

1. Starts with `op_`: the screen for that kind. An unknown kind opens home with
   a "this link needs a newer app version" message, never a link lookup.
2. A UUID: that business.
3. Anything else: a link name.

## Kinds

| Kind | Payload | Screen | Server |
|---|---|---|---|
| `delete-account` | 32 lowercase hex characters, a challenge | Confirm deleting the account (`ConfirmableAction.DELETE_ACCOUNT`) | [Confirm an action through Telegram](operations/authorization/telegram-confirm-action.md) |
| `export-personal` | 32 lowercase hex characters, a challenge | Confirm exporting the user's personal data (`ConfirmableAction.EXPORT_PERSONAL_DATA`) | [Confirm an action through Telegram](operations/authorization/telegram-confirm-action.md) |

The server builds and parses these in `TelegramLaunchParameter` (auth). It
trusts `start_param` only as a challenge id on `POST
/api/auth/telegram/confirm_action`; sign-in ignores it.

## Links

The bot's buttons open the bot's main Mini App,
`https://t.me/<bot username>?startapp=<start_param>`
(`APPLICATION_TELEGRAM_BOT_USERNAME`), so each bot must have BookkMe set as its
main Mini App in BotFather.

package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.telegram.entity.TelegramActionConfirmationRequest
import com.bookk.auth.domain.api.telegram.entity.TelegramActionConfirmationStart
import com.bookk.auth.domain.api.telegram.operation.ConfirmActionWithTelegram
import com.bookk.auth.domain.api.telegram.operation.StartTelegramActionConfirmation
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.fromAcceptLanguage
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.http.HttpHeaders
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.telegramConfirmAction() {
    authenticate {
        /**
         * Start confirming an action through Telegram
         *
         * Summary: Start confirming an action through Telegram
         * Description: For an account linked to Telegram, with or without a passkey, since the Mini App can't use one. The bot sends the caller a message naming the action with a button that opens the Mini App with start_param op_(kind)_(challenge), see docs/telegram-launch-parameters.md. The challenge lasts 5 minutes. Only DELETE_ACCOUNT and EXPORT_PERSONAL_DATA with the nil business id and the caller as target are accepted. One start a minute per user, given back when the message isn't sent. TELEGRAM_CONFIRMATION_UNAVAILABLE also covers a failed send and an account whose Telegram chat notifications hasn't stored yet, so retry later. Message language comes from Accept-Language. On TELEGRAM_CHAT_UNREACHABLE the Mini App can call Telegram.WebApp.requestWriteAccess() and start again.
         * Tag: auth
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.auth.domain.api.telegram.entity.TelegramActionConfirmationStart] Action, business id and target id
         * Response: 204 application/x-protobuf The bot sent the confirmation link
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Start errors<br>ACTION_NOT_CONFIRMABLE_THROUGH_TELEGRAM (24) Only deleting your own account or exporting your own data can be confirmed through Telegram<br>TELEGRAM_IDENTITY_MISSING (19) This account is not linked to Telegram<br>TELEGRAM_CHAT_UNREACHABLE (22) The bot can't write to you on Telegram, allow it to send messages and try again
         * Response: 429 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] TELEGRAM_CONFIRMATION_TOO_FREQUENT (23) Wait a minute before asking for another Telegram confirmation
         * Response: 503 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] TELEGRAM_CONFIRMATION_UNAVAILABLE (25) Telegram confirmation is not available right now, try again later
         * See: docs/operations/authorization/telegram-confirm-action-start.md
         */
        post<Api.Auth.Telegram.ConfirmAction.Start> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<TelegramActionConfirmationStart>()
            val language = Language.fromAcceptLanguage(call.request.headers[HttpHeaders.AcceptLanguage])
            val startTelegramActionConfirmation by application.injectScoped<StartTelegramActionConfirmation>(
                AuthScope,
            )

            call.respondWith(startTelegramActionConfirmation(principal.userId, body, language))
        }

        /**
         * Confirm an action through Telegram
         *
         * Summary: Confirm an action through Telegram
         * Description: Send the raw Telegram.WebApp.initData of the Mini App opened from the bot's confirmation button. It must be signed by Telegram for this server's bot, at most 5 minutes old, carry start_param op_(kind)_(challenge) of a challenge the caller started, and come from the Telegram account linked to the caller. The challenge is used once, and only when all of that holds, so a forwarded button doesn't spend it. Answers the same action confirmation token as POST passkey/confirm_action, for the action, business and target of the challenge.
         * Tag: auth
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.auth.domain.api.telegram.entity.TelegramActionConfirmationRequest] Mini App init data
         * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationToken] Action confirmation token
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Confirmation errors<br>TELEGRAM_DATA_INVALID (14) Telegram data is not signed by Telegram for this bot<br>TELEGRAM_DATA_EXPIRED (15) Telegram data is too old, reopen the Mini App<br>TELEGRAM_CONFIRMATION_EXPIRED (20) Telegram confirmation expired or was already used, ask for a new one<br>TELEGRAM_ACCOUNT_MISMATCH (21) The confirmation was opened from a Telegram account not linked to this account
         * Response: 503 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] TELEGRAM_CONFIRMATION_UNAVAILABLE (25) Telegram confirmation is not available right now, try again later
         * See: docs/operations/authorization/telegram-confirm-action.md
         */
        post<Api.Auth.Telegram.ConfirmAction> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<TelegramActionConfirmationRequest>()
            val confirmActionWithTelegram by application.injectScoped<ConfirmActionWithTelegram>(AuthScope)

            call.respondWith(confirmActionWithTelegram(principal.userId, body))
        }
    }
}

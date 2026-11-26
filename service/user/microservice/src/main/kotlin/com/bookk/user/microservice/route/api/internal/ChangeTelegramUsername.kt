package com.bookk.user.microservice.route.api.internal

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.user.domain.api.entity.TelegramUsernameBody
import com.bookk.user.domain.api.operation.ChangeTelegramUsername
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.microservice.route.UserRouting.Api
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.postChangeTelegramUsername() {
    /**
     * Store the Telegram username
     *
     * Summary: Store the Telegram username
     * Description: Store the username auth read from signed Telegram Mini App data, or clear it when the body has none. The profile is updated and announced only when the username changed.
     * Tag: internal
     * Body: application/x-protobuf [com.bookk.user.domain.api.entity.TelegramUsernameBody] The current Telegram username
     * Response: 204 application/x-protobuf Username stored
     * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] User errors<br>USER_NOT_EXIST (100001) User not found
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Change Telegram username errors<br>TELEGRAM_USERNAME_INVALID (100007) Telegram username is invalid
     * See: docs/operations/user/change-telegram-username-internal.md
     */
    post<Api.Internal.User.ChangeTelegramUsername> { path ->
        val body = call.receive<TelegramUsernameBody>()
        val changeTelegramUsername by application.injectScoped<ChangeTelegramUsername>(UserScope)
        call.respondWith(changeTelegramUsername(path.id, body.username))
    }
}

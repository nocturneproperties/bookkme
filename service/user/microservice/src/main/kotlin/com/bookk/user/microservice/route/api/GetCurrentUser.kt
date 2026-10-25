package com.bookk.user.microservice.route.api

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import com.bookk.user.domain.api.operation.GetUserById
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.microservice.route.UserRouting.Api
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.getCurrentUser() {
    authenticate {
        /**
         * Summary: Get current user
         * Description: Get user profile from current authentication token.
         * Tag: user
         * Security: jwt
         * Response: 200 application/x-protobuf [com.bookk.user.domain.api.entity.User] User associated with provided credentials
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] User errors<br>USER_NOT_EXIST (100001) User not found
         */
        get<Api.User.Me> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getCurrentUser by application.injectScoped<GetUserById>(UserScope)

            call.respondWith(getCurrentUser(principal.userId))
        }
    }
}

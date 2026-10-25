package com.bookk.user.microservice.route.api

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import com.bookk.user.domain.api.entity.UserEditModel
import com.bookk.user.domain.api.operation.EditUser
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.microservice.route.UserRouting.Api
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.patch
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.patchUser() {
    authenticate {
        /**
         * Summary: Update user
         * Description: Update current user from authentication token.
         * Tag: user
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.user.domain.api.entity.UserEditModel] Fields that needs to be updated
         * Response: 200 application/x-protobuf User successfully updated
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] User errors<br>USER_NOT_EXIST (100001) User not found
         * See: docs/operations/user/update-user.md
         */
        patch<Api.User.Me> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<UserEditModel>()
            val editUser by application.injectScoped<EditUser>(UserScope)

            call.respondWith(editUser(principal.userId, body))
        }
    }
}

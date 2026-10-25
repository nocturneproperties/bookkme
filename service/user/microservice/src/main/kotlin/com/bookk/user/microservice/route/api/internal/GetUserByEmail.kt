package com.bookk.user.microservice.route.api.internal

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.user.domain.api.entity.EmailBody
import com.bookk.user.domain.api.operation.GetUserByEmail
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.microservice.route.UserRouting.Api
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.getUserByEmail() {
    /**
     * Summary: Get user by email
     * Description: Get user profile by email.
     * Tag: internal
     * Body: application/x-protobuf [com.bookk.user.domain.api.entity.EmailBody] Email to search by
     * Response: 200 application/x-protobuf [com.bookk.user.domain.api.entity.User] User associated with this email
     * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] User errors<br>USER_NOT_EXIST (100001) User not exist
     */
    get<Api.Internal.User.Email> {
        val getUserByEmail by application.injectScoped<GetUserByEmail>(UserScope)
        val email = call.receive<EmailBody>()
        call.respondWith(getUserByEmail(email))
    }
}

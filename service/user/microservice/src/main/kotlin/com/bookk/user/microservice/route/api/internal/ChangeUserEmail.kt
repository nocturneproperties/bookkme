package com.bookk.user.microservice.route.api.internal

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.user.domain.api.entity.EmailBody
import com.bookk.user.domain.api.operation.ChangeUserEmail
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.microservice.route.UserRouting.Api
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.postChangeUserEmail() {
    /**
     * Store a verified email
     *
     * Summary: Store a verified email
     * Description: Store an email address auth has just verified with a code as the user's address, mark it verified and clear the verification due time.
     * Tag: internal
     * Body: application/x-protobuf [com.bookk.user.domain.api.entity.EmailBody] The verified address
     * Response: 204 application/x-protobuf Email stored and marked verified
     * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] User errors<br>USER_NOT_EXIST (100001) User not found
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Change email errors<br>USER_CONTACT_TAKEN (100002) Phone or email is already used by another account
     * See: docs/operations/user/change-user-email-internal.md
     */
    post<Api.Internal.User.ChangeEmail> { path ->
        val body = call.receive<EmailBody>()
        val changeUserEmail by application.injectScoped<ChangeUserEmail>(UserScope)
        call.respondWith(changeUserEmail(path.id, body.email))
    }
}

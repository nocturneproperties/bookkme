package com.bookk.user.microservice.route.api.internal

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.user.domain.api.entity.NewUser
import com.bookk.user.domain.api.operation.CreateUser
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.microservice.route.UserRouting.Api
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.postCreateUser() {
    /**
     * Create new user
     *
     * Summary: Create new user
     * Description: Create new user from provided info
     * Tag: internal
     * Body: application/x-protobuf [com.bookk.user.domain.api.entity.NewUser] User to create and the legal document versions they accepted at sign-up
     * Response: 200 application/x-protobuf [com.bookk.user.domain.api.entity.UserId] User created successfully, id of the new user returned.
     * Response: 400 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Error while creating new user
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create user errors<br>LEGAL_DOCUMENT_VERSION_UNKNOWN (100003) Legal document version is not published<br>LEGAL_DOCUMENTS_NOT_ACCEPTED (100004) Every legal document in effect must be accepted
     * See: docs/operations/user/create-user-internal.md
     */
    post<Api.Internal.User> {
        val newUser = call.receive<NewUser>()
        val createUser by application.injectScoped<CreateUser>(UserScope)
        call.respondWith(createUser(newUser))
    }
}

package com.bookk.user.microservice.route.api.internal

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.operation.CreateUser
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.microservice.route.UserRouting.Api
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.postCreateUser() {
    /**
     * Summary: Create new user
     * Description: Create new user from provided info
     * Tag: internal
     * Body: application/x-protobuf [com.bookk.user.domain.api.entity.User] User to create, id is ignored
     * Response: 201 application/x-protobuf [com.bookk.user.domain.api.entity.UserId] User created successfully, id of the new user returned.
     * Response: 400 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Error while creating new user
     * See: docs/operations/user/create-user-internal.md
     */
    post<Api.Internal.User> {
        val user = call.receive<User>()
        val createUser by application.injectScoped<CreateUser>(UserScope)
        call.respondWith(createUser(user))
    }
}

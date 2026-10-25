package com.bookk.user.microservice.route.api.internal

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.user.domain.api.operation.GetUserById
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.microservice.route.UserRouting.Api
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.getUserById() {
    /**
     * Summary: Get user by id
     * Description: Get user profile by email.
     * Tag: internal
     * Response: 200 application/x-protobuf [com.bookk.user.domain.api.entity.User] User associated with id
     * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] User errors<br>USER_NOT_EXIST (100001) User not exist
     */
    get<Api.Internal.User.Id> { user ->
        val getUserById by application.injectScoped<GetUserById>(UserScope)
        call.respondWith(getUserById(user.id))
    }
}

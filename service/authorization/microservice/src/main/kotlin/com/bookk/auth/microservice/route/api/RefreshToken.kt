package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.token.operation.RefreshToken
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.postRefreshToken() {
    /**
     * Summary: Refresh access token
     * Description: This request generates new access/refresh pair.
     * Tag: auth
     * Header: Authorization Bearer &lt;refresh token&gt;
     * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.token.entity.AuthTokens] New access/refresh pair
     * Response: 401 Missing or malformed refresh token
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Refresh errors<br>INVALID_CREDENTIALS (400001) Invalid refresh token
     * See: docs/operations/authorization/refresh-token.md
     */
    post<Api.Auth.Refresh> {
        val refreshToken by application.injectScoped<RefreshToken>(AuthScope)
        val token = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")

        if (token.isNullOrBlank()) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        call.respondWith(refreshToken(token))
    }
}

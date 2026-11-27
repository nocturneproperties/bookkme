package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.demo.operation.DemoUpgradeError
import com.bookk.core.service.enity.respondWith
import io.ktor.server.routing.RoutingCall

internal suspend fun RoutingCall.respondDemoUpgradeRequired() {
    respondWith(Result.failure<Unit>(DemoUpgradeError.UpgradeRequired()))
}

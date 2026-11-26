package com.bookk.user.microservice.route.api.internal

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.user.domain.api.entity.DemoUpgradeProfileBody
import com.bookk.user.domain.api.operation.UpgradeDemoProfile
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.microservice.route.UserRouting.Api
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.postUpgradeDemoProfile() {
    /**
     * Replace a demo profile
     *
     * Summary: Replace a demo profile
     * Description: Replace a demo account's placeholder email and name with the address auth has just verified with a code and the name the user gave, in one write, when the demo is upgraded to a regular account.
     * Tag: internal
     * Body: application/x-protobuf [com.bookk.user.domain.api.entity.DemoUpgradeProfileBody] The verified address and the new name
     * Response: 204 application/x-protobuf Email and name stored
     * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] User errors<br>USER_NOT_EXIST (100001) User not found
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Demo profile errors<br>USER_CONTACT_TAKEN (100002) Phone or email is already used by another account
     * See: docs/operations/user/upgrade-demo-profile-internal.md
     */
    post<Api.Internal.User.UpgradeDemoProfile> { path ->
        val body = call.receive<DemoUpgradeProfileBody>()
        val upgradeDemoProfile by application.injectScoped<UpgradeDemoProfile>(UserScope)
        call.respondWith(upgradeDemoProfile(path.id, body.email, body.name))
    }
}

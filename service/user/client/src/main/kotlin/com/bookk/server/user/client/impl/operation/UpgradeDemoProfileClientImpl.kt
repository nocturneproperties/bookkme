package com.bookk.server.user.client.impl.operation

import com.bookk.core.client.throwOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.user.client.impl.UserRouting
import com.bookk.user.domain.api.entity.DemoUpgradeProfileBody
import com.bookk.user.domain.api.entity.ProfileName
import com.bookk.user.domain.api.operation.UpgradeDemoProfile
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

internal class UpgradeDemoProfileClientImpl(
    private val httpClient: HttpClient,
) : UpgradeDemoProfile {

    override suspend fun invoke(
        userId: Uuid,
        verifiedEmail: String,
        name: ProfileName,
    ): Result<Unit> = runSuspendCatching {
        httpClient.post(UserRouting.Api.Internal.User.UpgradeDemoProfile(id = userId)) {
            setBody(DemoUpgradeProfileBody(verifiedEmail, name))
        }.throwOnFailure()
    }
}

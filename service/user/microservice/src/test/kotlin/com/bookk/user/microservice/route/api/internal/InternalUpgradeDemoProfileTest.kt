package com.bookk.user.microservice.route.api.internal

import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.domain.api.entity.DemoUpgradeProfileBody
import com.bookk.user.domain.api.entity.ProfileName
import com.bookk.user.domain.api.error.UserErrorCodes
import com.bookk.user.domain.api.operation.UpgradeDemoProfile
import com.bookk.user.microservice.route.UserRouting
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class InternalUpgradeDemoProfileTest {

    @Test
    fun `should store the verified email and name of the user in the path`() = routeTest {
        given()
        val useCase: UpgradeDemoProfile = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, EMAIL, NAME) } returns Result.success(Unit)
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postUpgradeDemoProfile() })

        whenn()
        val response = createTestClient().post(UserRouting.Api.Internal.User.UpgradeDemoProfile(id = userId)) {
            setBody(DemoUpgradeProfileBody(EMAIL, NAME))
        }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { useCase.invoke(userId, EMAIL, NAME) }
    }

    @Test
    fun `should return 404 when the user does not exist`() = routeTest {
        given()
        val useCase: UpgradeDemoProfile = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, EMAIL, NAME) } returns Result.failure(UpgradeDemoProfile.Error.UserNotFound())
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postUpgradeDemoProfile() })

        whenn()
        val response = createTestClient().post(UserRouting.Api.Internal.User.UpgradeDemoProfile(id = userId)) {
            setBody(DemoUpgradeProfileBody(EMAIL, NAME))
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(UserErrorCodes.USER_NOT_EXIST, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return 422 when another account holds the email`() = routeTest {
        given()
        val useCase: UpgradeDemoProfile = mockk()
        val userId = Uuid.random()
        coEvery { useCase.invoke(userId, EMAIL, NAME) } returns Result.failure(UpgradeDemoProfile.Error.EmailTaken())
        setupApplication(diModule = module { single { useCase } }, routeUnderTest = { postUpgradeDemoProfile() })

        whenn()
        val response = createTestClient().post(UserRouting.Api.Internal.User.UpgradeDemoProfile(id = userId)) {
            setBody(DemoUpgradeProfileBody(EMAIL, NAME))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(UserErrorCodes.USER_CONTACT_TAKEN, response.body<SimpleServerError>().errorCode)
    }

    private companion object {
        const val EMAIL = "ann@example.com"
        val NAME = ProfileName.stub(name = "Ann", lastName = "Lee")
    }
}

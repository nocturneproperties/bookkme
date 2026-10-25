package com.bookk.user.microservice.route.api

import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserEditModel
import com.bookk.user.domain.api.error.UserErrorCodes
import com.bookk.user.domain.api.operation.EditUser
import com.bookk.user.domain.api.operation.GetUserById
import com.bookk.user.microservice.route.UserRouting
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.patch
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class UserMeTest {

    private val userId = Uuid.random()

    @Test
    fun `should get current user`() = routeTest {
        given()
        val useCase: GetUserById = mockk()
        val user = User.stub(id = userId)
        
        coEvery { useCase.invoke(userId) } returns Result.success(user)
        
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { getCurrentUser() }
        )
        
        whenn()
        val client = createTestClient()
        val response = client.get(UserRouting.Api.User.Me())
        
        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should patch user`() = routeTest {
        given()
        val useCase: EditUser = mockk()
        val editModel = UserEditModel(id = userId, firstName = "NewName", lastName = "NewLastName", email = "test@example.com", phone = null)

        coEvery { useCase.invoke(userId, editModel) } returns Result.success(Unit)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { patchUser() }
        )

        whenn()
        val client = createTestClient()
        val response = client.patch(UserRouting.Api.User.Me()) {
            setBody(editModel)
        }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should return not found when user does not exist on get`() = routeTest {
        given()
        val useCase: GetUserById = mockk()
        coEvery { useCase.invoke(userId) } returns Result.failure(GetUserById.Error.UserNotFound())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { getCurrentUser() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(UserRouting.Api.User.Me())

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(UserErrorCodes.USER_NOT_EXIST, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when user does not exist on patch`() = routeTest {
        given()
        val useCase: EditUser = mockk()
        val editModel = UserEditModel(id = userId, firstName = "NewName", lastName = "NewLastName", email = "test@example.com", phone = null)
        coEvery { useCase.invoke(userId, editModel) } returns Result.failure(EditUser.Error.UserNotFound())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { patchUser() }
        )

        whenn()
        val client = createTestClient()
        val response = client.patch(UserRouting.Api.User.Me()) {
            setBody(editModel)
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(UserErrorCodes.USER_NOT_EXIST, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when getting current user without authentication`() = routeTest {
        given()
        val useCase: GetUserById = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { getCurrentUser() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(UserRouting.Api.User.Me())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unauthorized when patching user without authentication`() = routeTest {
        given()
        val useCase: EditUser = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { patchUser() }
        )

        whenn()
        val client = createTestClient()
        val response = client.patch(UserRouting.Api.User.Me()) {
            setBody(UserEditModel(id = userId, firstName = "N", lastName = "L", email = "e@e.com", phone = null))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}

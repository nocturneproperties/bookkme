package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.business.entity.BusinessLink
import com.bookk.business.domain.api.business.operation.GetBusinessLink
import com.bookk.business.domain.api.business.operation.ResolveBusinessLink
import com.bookk.business.domain.api.business.operation.SetBusinessLink
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.business.microservice.route.BusinessRouting
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class BusinessLinkTest {

    private class SutFixture {
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val setBusinessLink = mockk<SetBusinessLink>()
        val getBusinessLink = mockk<GetBusinessLink>()
        val resolveBusinessLink = mockk<ResolveBusinessLink>()
        val linkRoute = BusinessRouting.Api.Business.Id.Link(BusinessRouting.Api.Business.Id(id = businessId))

        val diModule: Module = module {
            single { setBusinessLink }
            single { getBusinessLink }
            single { resolveBusinessLink }
        }

        fun authenticated(): Application.() -> Unit {
            return {
                install(Authentication) {
                    provider { authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) } }
                }
            }
        }

        fun authenticatedDemo(): Application.() -> Unit {
            return {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random(), isDemo = true)) }
                    }
                }
            }
        }

        fun unauthenticated(): Application.() -> Unit {
            return { install(Authentication) { bearer { authenticate { null } } } }
        }

        fun ApplicationTestBuilder.start(extension: Application.() -> Unit) {
            setupApplication(extension = extension, diModule = diModule, routeUnderTest = { businessLink() })
        }
    }

    @Test
    fun `should set the business link`() = routeTest {
        given()
        val fixture = SutFixture()
        val link = BusinessLink(fixture.businessId, "my-shop")
        coEvery { fixture.setBusinessLink(fixture.userId, fixture.businessId, "My-Shop", false) } returns
            Result.success(link)
        with(fixture) { start(authenticated()) }

        whenn()
        val response = createTestClient().put(fixture.linkRoute) { setBody(BusinessLinkRequest(name = "My-Shop")) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(link, response.body<BusinessLink>())
    }

    @Test
    fun `should return unprocessable entity for every rejected link name`() = routeTest {
        given()
        val fixture = SutFixture()
        val errors: List<Pair<BusinessError, Int>> = listOf(
            SetBusinessLink.Error.LinkInvalid() to BusinessErrorCodes.BUSINESS_LINK_INVALID,
            SetBusinessLink.Error.LinkReserved() to BusinessErrorCodes.BUSINESS_LINK_RESERVED,
            SetBusinessLink.Error.LinkTaken() to BusinessErrorCodes.BUSINESS_LINK_TAKEN,
            SetBusinessLink.Error.ChangeLimitReached() to BusinessErrorCodes.BUSINESS_LINK_CHANGE_LIMIT_REACHED,
            SetBusinessLink.Error.DemoRestricted() to BusinessErrorCodes.BUSINESS_DEMO_RESTRICTED,
        )
        coEvery {
            fixture.setBusinessLink(fixture.userId, fixture.businessId, any(), any())
        } returnsMany errors.map { Result.failure(it.first) }
        with(fixture) { start(authenticated()) }

        whenn()
        val client = createTestClient()
        val responses = errors.map {
            client.put(fixture.linkRoute) { setBody(BusinessLinkRequest(name = "my-shop")) }
        }

        then()
        assertEquals(List(errors.size) { HttpStatusCode.UnprocessableEntity }, responses.map { it.status })
        assertEquals(errors.map { it.second }, responses.map { it.body<SimpleServerError>().errorCode })
    }

    @Test
    fun `should tell the operation that a demo account sets the link`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery {
            fixture.setBusinessLink(fixture.userId, fixture.businessId, "my-shop", true)
        } returns Result.failure(SetBusinessLink.Error.DemoRestricted())
        with(fixture) { start(authenticatedDemo()) }

        whenn()
        val response = createTestClient().put(fixture.linkRoute) { setBody(BusinessLinkRequest(name = "my-shop")) }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_DEMO_RESTRICTED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when the caller does not own the business`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery {
            fixture.setBusinessLink(fixture.userId, fixture.businessId, "my-shop", false)
        } returns Result.failure(Error.OperationNotAllowed())
        with(fixture) { start(authenticated()) }

        whenn()
        val response = createTestClient().put(fixture.linkRoute) { setBody(BusinessLinkRequest(name = "my-shop")) }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unauthorized when setting the link without authentication`() = routeTest {
        given()
        val fixture = SutFixture()
        with(fixture) { start(unauthenticated()) }

        whenn()
        val response = createTestClient().put(fixture.linkRoute) { setBody(BusinessLinkRequest(name = "my-shop")) }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return the business link`() = routeTest {
        given()
        val fixture = SutFixture()
        val link = BusinessLink(fixture.businessId, "my-shop")
        coEvery { fixture.getBusinessLink(fixture.userId, fixture.businessId) } returns Result.success(link)
        with(fixture) { start(authenticated()) }

        whenn()
        val response = createTestClient().get(fixture.linkRoute)

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(link, response.body<BusinessLink>())
    }

    @Test
    fun `should return not found when the business has no link`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery {
            fixture.getBusinessLink(fixture.userId, fixture.businessId)
        } returns Result.failure(GetBusinessLink.Error.LinkNotFound())
        with(fixture) { start(authenticated()) }

        whenn()
        val response = createTestClient().get(fixture.linkRoute)

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_LINK_NOT_FOUND, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when the caller cannot view the business link`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery {
            fixture.getBusinessLink(fixture.userId, fixture.businessId)
        } returns Result.failure(Error.OperationNotAllowed())
        with(fixture) { start(authenticated()) }

        whenn()
        val response = createTestClient().get(fixture.linkRoute)

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return forbidden when the caller's employee access is suspended`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery {
            fixture.getBusinessLink(fixture.userId, fixture.businessId)
        } returns Result.failure(EmployeeAccessSuspended())
        with(fixture) { start(authenticated()) }

        whenn()
        val response = createTestClient().get(fixture.linkRoute)

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `should return unauthorized when getting the link without authentication`() = routeTest {
        given()
        val fixture = SutFixture()
        with(fixture) { start(unauthenticated()) }

        whenn()
        val response = createTestClient().get(fixture.linkRoute)

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should resolve a link name without authentication`() = routeTest {
        given()
        val fixture = SutFixture()
        val link = BusinessLink(fixture.businessId, "my-shop")
        coEvery { fixture.resolveBusinessLink("my-shop") } returns Result.success(link)
        with(fixture) { start(unauthenticated()) }

        whenn()
        val response = createTestClient().get(BusinessRouting.Api.Business.LinkName(name = "my-shop"))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(link, response.body<BusinessLink>())
    }

    @Test
    fun `should return not found when resolving a link name no business uses`() = routeTest {
        given()
        val fixture = SutFixture()
        coEvery {
            fixture.resolveBusinessLink("my-shop")
        } returns Result.failure(ResolveBusinessLink.Error.LinkNotFound())
        with(fixture) { start(unauthenticated()) }

        whenn()
        val response = createTestClient().get(BusinessRouting.Api.Business.LinkName(name = "my-shop"))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_LINK_NOT_FOUND, response.body<SimpleServerError>().errorCode)
    }
}

package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.client.operation.GetClientBusinessIds
import com.bookk.business.microservice.route.BusinessRouting
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class GetClientBusinessIdsTest {

    private val userId = Uuid.random()

    private fun businessesResource() = BusinessRouting.Api.Internal.Client.Businesses(
        parent = BusinessRouting.Api.Internal.Client(),
        userId = userId
    )

    @Test
    fun `should return business ids the user is a client of`() = routeTest {
        given()
        val useCase: GetClientBusinessIds = mockk()
        val businessIds = listOf(Uuid.random(), Uuid.random())
        coEvery { useCase.invoke(userId) } returns Result.success(businessIds)

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getClientBusinessIds() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(businessesResource())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(businessIds, response.body<List<Uuid>>())
    }

    @Test
    fun `should return empty list when the user has no client relations`() = routeTest {
        given()
        val useCase: GetClientBusinessIds = mockk()
        coEvery { useCase.invoke(userId) } returns Result.success(emptyList())

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getClientBusinessIds() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(businessesResource())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList<Uuid>(), response.body<List<Uuid>>())
    }
}

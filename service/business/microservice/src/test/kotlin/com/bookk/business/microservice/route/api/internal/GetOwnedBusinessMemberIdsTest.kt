package com.bookk.business.microservice.route.api.internal

import com.bookk.business.domain.api.employee.operation.GetOwnedBusinessMemberIds
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

internal class GetOwnedBusinessMemberIdsTest {

    private val ownerId = Uuid.random()

    private fun membersResource() = BusinessRouting.Api.Internal.Owner.Members(
        parent = BusinessRouting.Api.Internal.Owner(),
        userId = ownerId,
    )

    @Test
    fun `should return the user ids of the other members of the owner's business`() = routeTest {
        given()
        val useCase: GetOwnedBusinessMemberIds = mockk()
        val members = listOf(Uuid.random(), Uuid.random())
        coEvery { useCase.invoke(ownerId) } returns Result.success(members)

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getOwnedBusinessMemberIds() },
        )

        whenn()
        val response = createTestClient().get(membersResource())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(members, response.body<List<Uuid>>())
    }

    @Test
    fun `should return an empty list for a user who owns no business`() = routeTest {
        given()
        val useCase: GetOwnedBusinessMemberIds = mockk()
        coEvery { useCase.invoke(ownerId) } returns Result.success(emptyList())

        setupApplication(
            diModule = module { single { useCase } },
            routeUnderTest = { getOwnedBusinessMemberIds() },
        )

        whenn()
        val response = createTestClient().get(membersResource())

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList<Uuid>(), response.body<List<Uuid>>())
    }
}

package com.bookk.server.user.client.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.user.client.api.CreateUserRequest
import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import com.bookk.user.domain.api.entity.NewUser
import com.bookk.user.domain.api.entity.UserId
import com.bookk.user.domain.api.operation.CreateUser
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class UserClientImplTest {

    private class SutFixture {
        val createUser = mockk<CreateUser>()
        val sut = UserClientImpl(mockk(), mockk(), createUser, mockk())
    }

    @Test
    fun `should create the user with the documents it accepted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val accepted = AcceptedLegalDocuments(privacyPolicy = "2026-01-01", termsOfService = "2025-06-01")
        val sent = slot<NewUser>()
        coEvery { fixture.createUser.invoke(capture(sent)) } returns Result.success(UserId(userId))

        whenn()
        val result = fixture.sut.createUser(CreateUserRequest("Ann", "Lee", "ann@example.com", accepted))

        then()
        assertEquals(userId, result.getOrNull())
        assertEquals(NewUser("Ann", "Lee", "ann@example.com", accepted), sent.captured)
    }
}

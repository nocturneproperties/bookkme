package com.bookk.business.domain.impl.operation.user

import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class AnonymizeUserProfileImplTest {

    private class SutFixture {
        val clientDataSource = mockk<ClientDataSource>()
        val employeeDataSource = mockk<EmployeeDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = AnonymizeUserProfileImpl(transactionManager, clientDataSource, employeeDataSource, businessPermissionDataSource)
    }

    @Test
    fun `should anonymize integrated clients and employees and delete permissions for the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.anonymizeClientsByUserId(userId) } returns 1
            coEvery { employeeDataSource.anonymizeEmployeesByUserId(userId) } returns 1
            coEvery { businessPermissionDataSource.deleteUserPermissions(userId) } returns Unit
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.clientDataSource.anonymizeClientsByUserId(userId) }
        coVerify(exactly = 1) { fixture.employeeDataSource.anonymizeEmployeesByUserId(userId) }
        coVerify(exactly = 1) { fixture.businessPermissionDataSource.deleteUserPermissions(userId) }
    }

    @Test
    fun `should succeed as no-op when the user has no clients or employees anywhere`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.anonymizeClientsByUserId(userId) } returns 0
            coEvery { employeeDataSource.anonymizeEmployeesByUserId(userId) } returns 0
            coEvery { businessPermissionDataSource.deleteUserPermissions(userId) } returns Unit
        }

        whenn()
        val result = fixture.sut(userId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.businessPermissionDataSource.deleteUserPermissions(userId) }
    }
}

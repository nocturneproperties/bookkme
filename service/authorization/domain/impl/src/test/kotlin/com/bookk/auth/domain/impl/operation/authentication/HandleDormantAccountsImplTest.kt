package com.bookk.auth.domain.impl.operation.authentication

import com.bookk.auth.domain.api.authentication.entity.DormancyStage
import com.bookk.auth.domain.api.authentication.entity.DormantAccount
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.impl.operation.AccountEraser
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.business.client.api.BusinessClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class HandleDormantAccountsImplTest {

    private class SutFixture {
        val accountDataSource = mockk<AccountDataSource>(relaxUnitFun = true)
        val businessClient = mockk<BusinessClient>()
        val accountEraser = mockk<AccountEraser>(relaxUnitFun = true)
        val eventProducer = mockk<StandardEventProducer>()
        val transactionManager = mockk<TransactionManager>()
        val warnings = mutableListOf<AuthEvent.AccountDormancyWarning>()
        val sentInTransaction = mutableListOf<Boolean>()
        val sut =
            HandleDormantAccountsImpl(
                accountDataSource,
                businessClient,
                accountEraser,
                eventProducer,
                transactionManager,
            )

        init {
            transactionManager.mockTransaction()
            coEvery { eventProducer.send(any<AuthEvent.AccountDormancyWarning>(), any()) } coAnswers {
                warnings += firstArg<AuthEvent.AccountDormancyWarning>()
                sentInTransaction += transactionManager.isInTransaction()
            }
            coEvery { businessClient.getOwnedBusinessMemberIds(any()) } returns Result.success(emptyList())
        }

        fun dormant(vararg accounts: DormantAccount) {
            coEvery { accountDataSource.getDormantAccounts(any()) } returns accounts.toList()
            accounts.forEach { coEvery { accountDataSource.getDormantAccountForUpdate(it.userId) } returns it }
        }
    }

    private fun now() = Clock.System.now()

    @Test
    fun `should look for accounts that reach three years without activity within 60 days`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val cutoff = slot<Instant>()
        coEvery { fixture.accountDataSource.getDormantAccounts(capture(cutoff)) } returns emptyList()
        val startedAt = now()

        whenn()
        fixture.sut()

        then()
        val finishedAt = now()
        val expected = { at: Instant -> at.minus(36, DateTimeUnit.MONTH, TimeZone.UTC) + 60.days }
        assertTrue(cutoff.captured in expected(startedAt)..expected(finishedAt))
    }

    @Test
    fun `should send the first warning 60 days before deletion and remember when`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = DormantAccount.stub(stage = DormancyStage.ACTIVE)
        fixture.dormant(account)
        val warnedAt = mutableListOf<Instant?>()
        coEvery {
            fixture.accountDataSource.setDormancyStage(
                account.userId,
                DormancyStage.SENT_60_DAY_WARNING,
                captureNullable(warnedAt),
            )
        } returns Unit
        val startedAt = now()

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        val warning = fixture.warnings.single()
        assertEquals(account.userId, warning.userId)
        assertFalse(warning.final)
        val recordedWarning = requireNotNull(warnedAt.single())
        assertEquals(recordedWarning + 60.days, warning.deletesAt)
        assertTrue(recordedWarning >= startedAt)
        assertEquals(listOf(true), fixture.sentInTransaction)
    }

    @Test
    fun `should send the second warning 30 days before deletion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val firstWarnedAt = now() - 31.days
        val account = DormantAccount.stub(stage = DormancyStage.SENT_60_DAY_WARNING, warnedAt = firstWarnedAt)
        fixture.dormant(account)

        whenn()
        fixture.sut()

        then()
        val warning = fixture.warnings.single()
        assertFalse(warning.final)
        assertEquals(firstWarnedAt + 60.days, warning.deletesAt)
        coVerify(exactly = 1) {
            fixture.accountDataSource.setDormancyStage(account.userId, DormancyStage.SENT_30_DAY_WARNING, firstWarnedAt)
        }
        assertEquals(listOf(true), fixture.sentInTransaction)
    }

    @Test
    fun `should wait 30 days after the first warning before the second one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.dormant(DormantAccount.stub(stage = DormancyStage.SENT_60_DAY_WARNING, warnedAt = now() - 10.days))

        whenn()
        fixture.sut()

        then()
        assertTrue(fixture.warnings.isEmpty())
        coVerify(exactly = 0) { fixture.accountDataSource.setDormancyStage(any(), any(), any()) }
    }

    @Test
    fun `should send the final warning 7 days before deletion`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val firstWarnedAt = now() - 54.days
        val account = DormantAccount.stub(stage = DormancyStage.SENT_30_DAY_WARNING, warnedAt = firstWarnedAt)
        fixture.dormant(account)

        whenn()
        fixture.sut()

        then()
        val warning = fixture.warnings.single()
        assertTrue(warning.final)
        assertEquals(firstWarnedAt + 60.days, warning.deletesAt)
        coVerify(exactly = 1) {
            fixture.accountDataSource.setDormancyStage(account.userId, DormancyStage.SENT_7_DAY_WARNING, firstWarnedAt)
        }
        assertEquals(listOf(true), fixture.sentInTransaction)
    }

    @Test
    fun `should wait until 7 days before deletion before the final warning`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.dormant(DormantAccount.stub(stage = DormancyStage.SENT_30_DAY_WARNING, warnedAt = now() - 52.days))

        whenn()
        fixture.sut()

        then()
        assertTrue(fixture.warnings.isEmpty())
        coVerify(exactly = 0) { fixture.accountDataSource.setDormancyStage(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
    }

    @Test
    fun `should delete the account 60 days after the first warning`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val account = DormantAccount.stub(stage = DormancyStage.SENT_7_DAY_WARNING, warnedAt = now() - 61.days)
        fixture.dormant(account)

        whenn()
        fixture.sut()

        then()
        coVerify(exactly = 1) { fixture.accountEraser.erase(account.authId, account.userId) }
        assertTrue(fixture.warnings.isEmpty())
    }

    @Test
    fun `should not delete the account before 60 days after the first warning`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.dormant(DormantAccount.stub(stage = DormancyStage.SENT_7_DAY_WARNING, warnedAt = now() - 55.days))

        whenn()
        fixture.sut()

        then()
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
    }

    @Test
    fun `should keep an owner while another member of their business was active within three years`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val owner = DormantAccount.stub(stage = DormancyStage.SENT_7_DAY_WARNING, warnedAt = now() - 61.days)
        val memberId = Uuid.random()
        fixture.dormant(owner)
        coEvery { fixture.businessClient.getOwnedBusinessMemberIds(owner.userId) } returns
            Result.success(listOf(memberId))
        coEvery { fixture.accountDataSource.getLastActiveAt(listOf(memberId)) } returns
            mapOf(memberId to now() - 30.days)

        whenn()
        fixture.sut()

        then()
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
        assertTrue(fixture.warnings.isEmpty())
    }

    @Test
    fun `should handle an owner whose whole business has been inactive for three years`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val owner = DormantAccount.stub(stage = DormancyStage.SENT_7_DAY_WARNING, warnedAt = now() - 61.days)
        val memberId = Uuid.random()
        fixture.dormant(owner)
        coEvery { fixture.businessClient.getOwnedBusinessMemberIds(owner.userId) } returns
            Result.success(listOf(memberId))
        coEvery { fixture.accountDataSource.getLastActiveAt(listOf(memberId)) } returns
            mapOf(memberId to now() - (4 * 365).days)

        whenn()
        fixture.sut()

        then()
        coVerify(exactly = 1) { fixture.accountEraser.erase(owner.authId, owner.userId) }
    }

    @Test
    fun `should leave an account alone when it changed after it was listed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val listed = DormantAccount.stub(stage = DormancyStage.SENT_7_DAY_WARNING, warnedAt = now() - 61.days)
        fixture.dormant(listed)
        coEvery { fixture.accountDataSource.getDormantAccountForUpdate(listed.userId) } returns
            listed.copy(lastActiveAt = now(), stage = DormancyStage.ACTIVE, warnedAt = null)

        whenn()
        fixture.sut()

        then()
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
        assertTrue(fixture.warnings.isEmpty())
    }

    @Test
    fun `should neither warn nor delete an account whose deletion is on hold`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.dormant(
            DormantAccount.stub(stage = DormancyStage.ACTIVE, deletionHold = true),
            DormantAccount.stub(
                stage = DormancyStage.SENT_7_DAY_WARNING,
                warnedAt = now() - 61.days,
                deletionHold = true,
            ),
        )

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
        coVerify(exactly = 0) { fixture.accountDataSource.setDormancyStage(any(), any(), any()) }
        assertTrue(fixture.warnings.isEmpty())
    }

    @Test
    fun `should leave an account alone when its deletion was put on hold after it was listed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val listed = DormantAccount.stub(stage = DormancyStage.SENT_7_DAY_WARNING, warnedAt = now() - 61.days)
        fixture.dormant(listed)
        coEvery { fixture.accountDataSource.getDormantAccountForUpdate(listed.userId) } returns
            listed.copy(deletionHold = true)

        whenn()
        fixture.sut()

        then()
        coVerify(exactly = 0) { fixture.accountEraser.erase(any(), any()) }
        assertTrue(fixture.warnings.isEmpty())
    }

    @Test
    fun `should succeed when no account is dormant`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.dormant()

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        assertTrue(fixture.warnings.isEmpty())
    }

    @Test
    fun `should succeed when every dormant account is handled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.dormant(
            DormantAccount.stub(stage = DormancyStage.ACTIVE),
            DormantAccount.stub(stage = DormancyStage.ACTIVE),
        )

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.isSuccess)
        assertEquals(2, fixture.warnings.size)
    }

    @Test
    fun `should report the first failure with the later ones suppressed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = DormantAccount.stub(stage = DormancyStage.ACTIVE)
        val second = DormantAccount.stub(stage = DormancyStage.ACTIVE)
        val third = DormantAccount.stub(stage = DormancyStage.ACTIVE)
        val firstError = IllegalStateException("first")
        val secondError = IllegalStateException("second")
        val thirdError = IllegalStateException("third")
        fixture.dormant(first, second, third)
        coEvery { fixture.businessClient.getOwnedBusinessMemberIds(first.userId) } returns Result.failure(firstError)
        coEvery { fixture.businessClient.getOwnedBusinessMemberIds(second.userId) } returns Result.failure(secondError)
        coEvery { fixture.businessClient.getOwnedBusinessMemberIds(third.userId) } returns Result.failure(thirdError)

        whenn()
        val result = fixture.sut()

        then()
        assertSame(firstError, result.exceptionOrNull())
        assertEquals(listOf(secondError, thirdError), firstError.suppressed.toList())
    }

    @Test
    fun `should still handle the other accounts when one fails and then report the failure`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val failing = DormantAccount.stub(stage = DormancyStage.ACTIVE)
        val other = DormantAccount.stub(stage = DormancyStage.ACTIVE)
        val error = IllegalStateException("business service down")
        fixture.dormant(failing, other)
        coEvery { fixture.businessClient.getOwnedBusinessMemberIds(failing.userId) } returns Result.failure(error)

        whenn()
        val result = fixture.sut()

        then()
        assertSame(error, result.exceptionOrNull())
        assertEquals(listOf(other.userId), fixture.warnings.map { it.userId })
    }
}

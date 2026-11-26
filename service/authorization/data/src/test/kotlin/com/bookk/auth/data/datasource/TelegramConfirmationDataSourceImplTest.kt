package com.bookk.auth.data.datasource

import com.bookk.auth.domain.api.telegram.entity.TelegramConfirmationChallenge
import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.uuid.Uuid

internal class TelegramConfirmationDataSourceImplTest {

    private class SutFixture {
        val timeSource = TestTimeSource()
        val sut = TelegramConfirmationDataSourceImpl(InMemoryCacheClient(timeSource = timeSource))
        val userId: Uuid = Uuid.random()
        val challenge = TelegramConfirmationChallenge.stub(userId = userId)
    }

    @Test
    fun `should claim the start cooldown once per user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val first = fixture.sut.claimStartCooldown(fixture.userId)
        val second = fixture.sut.claimStartCooldown(fixture.userId)
        val otherUser = fixture.sut.claimStartCooldown(Uuid.random())

        then()
        assertTrue(first)
        assertFalse(second)
        assertTrue(otherUser)
    }

    @Test
    fun `should let the user start again once the 60 second cooldown has passed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.claimStartCooldown(fixture.userId)
        fixture.timeSource += 59.seconds
        val stillCooling = fixture.sut.claimStartCooldown(fixture.userId)
        fixture.timeSource += 1.seconds

        whenn()
        val claimed = fixture.sut.claimStartCooldown(fixture.userId)

        then()
        assertFalse(stillCooling)
        assertTrue(claimed)
    }

    @Test
    fun `should let the user start again right away once the cooldown is released`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.claimStartCooldown(fixture.userId)
        fixture.sut.releaseStartCooldown(fixture.userId)

        whenn()
        val claimed = fixture.sut.claimStartCooldown(fixture.userId)

        then()
        assertTrue(claimed)
    }

    @Test
    fun `should give back a saved challenge once`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.saveChallenge(CHALLENGE_ID, fixture.challenge)

        whenn()
        val first = fixture.sut.takeChallenge(CHALLENGE_ID)
        val second = fixture.sut.takeChallenge(CHALLENGE_ID)

        then()
        assertEquals(fixture.challenge, first)
        assertNull(second)
    }

    @Test
    fun `should read a saved challenge without taking it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.saveChallenge(CHALLENGE_ID, fixture.challenge)

        whenn()
        val read = fixture.sut.getChallenge(CHALLENGE_ID)
        val taken = fixture.sut.takeChallenge(CHALLENGE_ID)

        then()
        assertEquals(fixture.challenge, read)
        assertEquals(fixture.challenge, taken)
    }

    @Test
    fun `should not read a challenge that was already taken`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.saveChallenge(CHALLENGE_ID, fixture.challenge)
        fixture.sut.takeChallenge(CHALLENGE_ID)

        whenn()
        val read = fixture.sut.getChallenge(CHALLENGE_ID)

        then()
        assertNull(read)
    }

    @Test
    fun `should give a challenge to only one of two concurrent takers`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.saveChallenge(CHALLENGE_ID, fixture.challenge)

        whenn()
        val taken = List(2) { async { fixture.sut.takeChallenge(CHALLENGE_ID) } }.awaitAll()

        then()
        assertEquals(listOf(fixture.challenge), taken.filterNotNull())
    }

    @Test
    fun `should forget a challenge after 5 minutes`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.saveChallenge(CHALLENGE_ID, fixture.challenge)
        fixture.timeSource += 5.minutes

        whenn()
        val taken = fixture.sut.takeChallenge(CHALLENGE_ID)

        then()
        assertNull(taken)
    }

    @Test
    fun `should find no challenge that was deleted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.saveChallenge(CHALLENGE_ID, fixture.challenge)
        fixture.sut.deleteChallenge(CHALLENGE_ID)

        whenn()
        val taken = fixture.sut.takeChallenge(CHALLENGE_ID)

        then()
        assertNull(taken)
    }

    @Test
    fun `should find no challenge that was never saved`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val taken = fixture.sut.takeChallenge(CHALLENGE_ID)

        then()
        assertNull(taken)
    }

    private companion object {
        const val CHALLENGE_ID = "0123456789abcdef0123456789abcdef"
    }
}

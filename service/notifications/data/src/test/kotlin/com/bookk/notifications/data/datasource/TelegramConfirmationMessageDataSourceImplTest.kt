package com.bookk.notifications.data.datasource

import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.entity.PendingTelegramConfirmation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource
import kotlin.uuid.Uuid

internal class TelegramConfirmationMessageDataSourceImplTest {

    private class SutFixture {
        val timeSource = TestTimeSource()
        val sut = TelegramConfirmationMessageDataSourceImpl(InMemoryCacheClient(timeSource = timeSource))
        val userId: Uuid = Uuid.random()
        val pending = PendingTelegramConfirmation.stub(challengeId = "first", messageId = 1)
    }

    @Test
    fun `should return the confirmation message remembered for the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.save(fixture.userId, fixture.pending)

        whenn()
        val remembered = fixture.sut.get(fixture.userId)
        val otherUser = fixture.sut.get(Uuid.random())

        then()
        assertEquals(fixture.pending, remembered)
        assertNull(otherUser)
    }

    @Test
    fun `should keep only the latest confirmation message of a user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val newer = PendingTelegramConfirmation.stub(challengeId = "second", messageId = 2)
        fixture.sut.save(fixture.userId, fixture.pending)
        fixture.sut.save(fixture.userId, newer)

        whenn()
        val remembered = fixture.sut.get(fixture.userId)

        then()
        assertEquals(newer, remembered)
    }

    @Test
    fun `should forget the message it was given`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.save(fixture.userId, fixture.pending)

        whenn()
        fixture.sut.forget(fixture.userId, fixture.pending)

        then()
        assertNull(fixture.sut.get(fixture.userId))
    }

    @Test
    fun `should not forget a newer message remembered after the one being forgotten`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val newer = PendingTelegramConfirmation.stub(challengeId = "second", messageId = 2)
        fixture.sut.save(fixture.userId, newer)

        whenn()
        fixture.sut.forget(fixture.userId, fixture.pending)

        then()
        assertEquals(newer, fixture.sut.get(fixture.userId))
    }

    @Test
    fun `should forget the message just before Telegram stops allowing the bot to delete it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.sut.save(fixture.userId, fixture.pending)
        fixture.timeSource += 47.hours - 1.minutes
        val stillRemembered = fixture.sut.get(fixture.userId)
        fixture.timeSource += 1.minutes

        whenn()
        val remembered = fixture.sut.get(fixture.userId)

        then()
        assertEquals(fixture.pending, stillRemembered)
        assertNull(remembered)
    }
}

package com.bookk.auth.domain.impl.telegram

import com.bookk.auth.domain.api.telegram.entity.TelegramUser
import com.bookk.auth.domain.api.telegram.operation.SignInWithTelegram
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class Ed25519TelegramInitDataVerifierTest {

    private class SutFixture(botId: Long? = BOT_ID, trustsTestSigner: Boolean = true) {
        val now = Instant.parse("2026-11-20T10:00:00Z")
        val signer = TelegramInitDataSigner(BOT_ID)
        val clock = object : Clock {
            override fun now(): Instant {
                return this@SutFixture.now
            }
        }
        val sut = if (trustsTestSigner) {
            Ed25519TelegramInitDataVerifier(botId = botId, clock = clock, telegramPublicKey = signer.publicKey)
        } else {
            Ed25519TelegramInitDataVerifier(botId = botId, clock = clock)
        }

        fun verify(initData: String): Result<TelegramUser> = runCatching { sut.verify(initData) }
    }

    @Test
    fun `should return the Telegram user from data Telegram signed for this bot`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.signed(authDate = fixture.now - 1.minutes)

        whenn()
        val result = fixture.verify(initData)

        then()
        assertEquals(
            TelegramUser(id = 123456789, firstName = "Ann", lastName = "Lee", username = "ann_lee"),
            result.getOrThrow(),
        )
    }

    @Test
    fun `should read a user without a last name`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.signed(
            authDate = fixture.now,
            user = """{"id":42,"first_name":"Ann & Co = \"best\"","language_code":"uk"}""",
        )

        whenn()
        val result = fixture.verify(initData)

        then()
        assertEquals(
            TelegramUser(id = 42, firstName = "Ann & Co = \"best\"", lastName = null, username = null),
            result.getOrThrow(),
        )
    }

    @Test
    fun `should read a collectible four letter username`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.signed(
            authDate = fixture.now,
            user = """{"id":42,"first_name":"Ann","username":"Ann1"}""",
        )

        whenn()
        val result = fixture.verify(initData)

        then()
        assertEquals("Ann1", result.getOrThrow().username)
    }

    @Test
    fun `should leave out a username that is not a Telegram username`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val usernames = listOf("ann", "a".repeat(33), "ann lee", "ann-lee", "")
        val initData = usernames.map { username ->
            fixture.signer.signed(
                authDate = fixture.now,
                user = """{"id":42,"first_name":"Ann","username":"$username"}""",
            )
        }

        whenn()
        val results = initData.map { fixture.verify(it) }

        then()
        assertEquals(usernames.map { null }, results.map { it.getOrThrow().username })
    }

    @Test
    fun `should refuse data whose user was changed after signing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.signed(authDate = fixture.now)
            .replace("123456789", "987654321")

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
    }

    @Test
    fun `should refuse data with a field added after signing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.signed(authDate = fixture.now) + "&start_param=admin"

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
    }

    @Test
    fun `should refuse data signed for another bot`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.signed(authDate = fixture.now, signedForBotId = BOT_ID + 1)

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
    }

    @Test
    fun `should refuse data signed with a key that is not Telegram's`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = TelegramInitDataSigner(BOT_ID).signed(authDate = fixture.now)

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
    }

    @Test
    fun `should refuse data without a signature`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.encode(
            mapOf("auth_date" to fixture.now.epochSeconds.toString(), "user" to """{"id":1,"first_name":"A"}"""),
        )

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
    }

    @Test
    fun `should refuse text that is not init data`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val results = listOf("", "garbage", "a=1&a=2", "signature=%%%").map { fixture.verify(it) }

        then()
        assertTrue(results.all { it.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid }) {
            results.toString()
        }
    }

    @Test
    fun `should refuse signed data without a user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.signed(authDate = fixture.now, user = null)

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
    }

    @Test
    fun `should refuse signed data whose user has no numeric id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.signed(authDate = fixture.now, user = """{"id":"abc","first_name":"Ann"}""")

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
    }

    @Test
    fun `should refuse signed data whose user has no first name`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.signed(authDate = fixture.now, user = """{"id":42,"last_name":"Lee"}""")

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
    }

    @Test
    fun `should refuse genuine data older than an hour as expired`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.signed(authDate = fixture.now - 1.hours - 1.minutes)

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataExpired) { result.toString() }
    }

    @Test
    fun `should accept data exactly an hour old`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.signed(authDate = fixture.now - 1.hours)

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.isSuccess) { result.toString() }
    }

    @Test
    fun `should refuse data dated more than a minute in the future`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val initData = fixture.signer.signed(authDate = fixture.now + 2.minutes)

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
    }

    @Test
    fun `should report Telegram sign in as unavailable when no bot is configured`() = runUnitTest {
        given()
        val fixture = SutFixture(botId = null)
        val initData = fixture.signer.signed(authDate = fixture.now)

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramSignInUnavailable) {
            result.toString()
        }
    }

    @Test
    fun `should verify against Telegram's production key by default`() = runUnitTest {
        given()
        val fixture = SutFixture(trustsTestSigner = false)
        val initData = fixture.signer.signed(authDate = fixture.now)

        whenn()
        val result = fixture.verify(initData)

        then()
        assertTrue(result.exceptionOrNull() is SignInWithTelegram.Error.TelegramDataInvalid) { result.toString() }
    }

    private companion object {
        const val BOT_ID = 7000000001L
    }
}

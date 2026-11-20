package com.bookk.auth.domain.impl.telegram

import com.bookk.auth.domain.api.telegram.entity.TelegramUser
import com.bookk.auth.domain.api.telegram.operation.SignInWithTelegram.Error.TelegramDataExpired
import com.bookk.auth.domain.api.telegram.operation.SignInWithTelegram.Error.TelegramDataInvalid
import com.bookk.auth.domain.api.telegram.operation.SignInWithTelegram.Error.TelegramSignInUnavailable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal class Ed25519TelegramInitDataVerifier(
    private val botId: Long?,
    private val clock: Clock,
    private val telegramPublicKey: PublicKey = publicKeyOf(TELEGRAM_PRODUCTION_PUBLIC_KEY),
) : TelegramInitDataVerifier {

    override fun verify(initData: String): TelegramUser {
        val botId = botId ?: throw TelegramSignInUnavailable()
        val fields = parseFields(initData) ?: throw TelegramDataInvalid()
        val signature = fields[SIGNATURE]?.let(::decodeSignature) ?: throw TelegramDataInvalid()
        if (!isSignedByTelegram(checkString(botId, fields), signature)) throw TelegramDataInvalid()
        requireFresh(fields[AUTH_DATE]?.toLongOrNull() ?: throw TelegramDataInvalid())
        return fields[USER]?.let(::parseUser) ?: throw TelegramDataInvalid()
    }

    private fun parseFields(initData: String): Map<String, String>? {
        return initData
            .split('&')
            .map { parsePair(it) ?: return null }
            .toMap()
    }

    private fun parsePair(pair: String): Pair<String, String>? {
        val separator = pair.indexOf('=')
        if (separator <= 0) return null
        val key = decode(pair.substring(0, separator)) ?: return null
        val value = decode(pair.substring(separator + 1)) ?: return null
        return key to value
    }

    private fun decode(text: String): String? {
        return runCatching { URLDecoder.decode(text, Charsets.UTF_8) }.getOrNull()
    }

    private fun decodeSignature(encoded: String): ByteArray? {
        return runCatching { Base64.getUrlDecoder().decode(encoded) }.getOrNull()
    }

    private fun checkString(botId: Long, fields: Map<String, String>): String {
        val signedPairs = fields.filterKeys { it != HASH && it != SIGNATURE }.entries
            .sortedBy { it.key }
            .joinToString("\n") { (key, value) -> "$key=$value" }
        return "$botId:WebAppData\n$signedPairs"
    }

    private fun isSignedByTelegram(checkString: String, signature: ByteArray): Boolean {
        return runCatching {
            Signature.getInstance(ALGORITHM)
                .apply {
                    initVerify(telegramPublicKey)
                    update(checkString.encodeToByteArray())
                }
                .verify(signature)
        }.getOrDefault(false)
    }

    private fun requireFresh(authDateSeconds: Long) {
        val authDate = Instant.fromEpochSeconds(authDateSeconds)
        val now = clock.now()
        if (authDate > now + ALLOWED_CLOCK_SKEW) throw TelegramDataInvalid()
        if (now - authDate > MAX_AGE) throw TelegramDataExpired()
    }

    private fun parseUser(json: String): TelegramUser? {
        val user = runCatching { userJson.decodeFromString<TelegramWebAppUser>(json) }.getOrNull() ?: return null
        return TelegramUser(id = user.id, firstName = user.firstName, lastName = user.lastName)
    }

    @Serializable
    private class TelegramWebAppUser(
        @SerialName("id") val id: Long,
        @SerialName("first_name") val firstName: String,
        @SerialName("last_name") val lastName: String? = null,
    )

    private companion object {
        const val TELEGRAM_PRODUCTION_PUBLIC_KEY = "e7bf03a2fa4602af4580703d88dda5bb59f32ed8b02a56c187fe7d34caed242d"
        const val ALGORITHM = "Ed25519"
        const val ED25519_X509_PREFIX = "302a300506032b6570032100"
        const val HASH = "hash"
        const val SIGNATURE = "signature"
        const val AUTH_DATE = "auth_date"
        const val USER = "user"
        val MAX_AGE = 1.hours
        val ALLOWED_CLOCK_SKEW = 1.minutes
        val userJson = Json { ignoreUnknownKeys = true }

        fun publicKeyOf(rawKeyHex: String): PublicKey {
            val encoded = (ED25519_X509_PREFIX + rawKeyHex).hexToByteArray()
            return KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(encoded))
        }
    }
}

package com.bookk.auth.domain.impl.telegram

import java.net.URLEncoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import kotlin.time.Instant

internal class TelegramInitDataSigner(val botId: Long = 7000000001) {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    val publicKey: PublicKey = keyPair.public

    fun signed(
        authDate: Instant,
        user: String? = """{"id":123456789,"first_name":"Ann","last_name":"Lee","username":"ann"}""",
        extra: Map<String, String> = mapOf("query_id" to "AAHdF6IQAAAAAN0XohDhrOrc"),
        signedForBotId: Long = botId,
    ): String {
        val fields = buildMap {
            putAll(extra)
            put("auth_date", authDate.epochSeconds.toString())
            user?.let { put("user", it) }
        }
        val signature = sign(checkString(signedForBotId, fields))
        return encode(fields + ("hash" to "0".repeat(64)) + ("signature" to signature))
    }

    fun encode(fields: Map<String, String>): String {
        return fields.entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    }

    private fun checkString(botId: Long, fields: Map<String, String>): String {
        val pairs = fields.entries.sortedBy { it.key }.joinToString("\n") { (key, value) -> "$key=$value" }
        return "$botId:WebAppData\n$pairs"
    }

    private fun sign(text: String): String {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(text.encodeToByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())
    }
}

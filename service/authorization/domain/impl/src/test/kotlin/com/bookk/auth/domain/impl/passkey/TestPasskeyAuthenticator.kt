package com.bookk.auth.domain.impl.passkey

import com.bookk.core.AppLevelConstants
import com.yubico.webauthn.RegisteredCredential
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import com.yubico.webauthn.data.ByteArray as YubicoByteArray

internal class TestPasskeyAuthenticator {
    private val keyPair: KeyPair = newKeyPair()

    val credentialId: ByteArray = ByteArray(CREDENTIAL_ID_LENGTH).also(SecureRandom()::nextBytes)

    val cosePublicKey: ByteArray = coseKeyOf(keyPair.public as ECPublicKey)

    fun registeredCredential(userHandle: ByteArray, signatureCount: Long = 0): RegisteredCredential {
        return RegisteredCredential.builder()
            .credentialId(YubicoByteArray(credentialId))
            .userHandle(YubicoByteArray(userHandle))
            .publicKeyCose(YubicoByteArray(cosePublicKey))
            .signatureCount(signatureCount)
            .build()
    }

    fun assertion(
        challenge: String,
        userHandle: ByteArray?,
        signatureCount: Int = 1,
        origin: String = "https://${AppLevelConstants.domainName}",
        signingKey: KeyPair = keyPair,
    ): String {
        val clientData = clientDataJson(type = "webauthn.get", challenge = challenge, origin = origin)
        val authenticatorData = rpIdHash() + byteArrayOf(USER_PRESENT_AND_VERIFIED) + counter(signatureCount)
        val signature = sign(signingKey, authenticatorData + sha256(clientData))
        val userHandleField = userHandle?.let { ""","userHandle":"${encode(it)}"""" }.orEmpty()
        return """
            {"type":"public-key","id":"${encode(credentialId)}","rawId":"${encode(credentialId)}",
            "response":{"authenticatorData":"${encode(authenticatorData)}","clientDataJSON":"${encode(clientData)}",
            "signature":"${encode(signature)}"$userHandleField},"clientExtensionResults":{}}
        """.trimIndent()
    }

    fun registration(challenge: String, origin: String = "https://${AppLevelConstants.domainName}"): String {
        val clientData = clientDataJson(type = "webauthn.create", challenge = challenge, origin = origin)
        val attestationObject = noneAttestationOf(attestedAuthenticatorData())
        return """
            {"type":"public-key","id":"${encode(credentialId)}","rawId":"${encode(credentialId)}",
            "response":{"attestationObject":"${encode(attestationObject)}","clientDataJSON":"${encode(clientData)}",
            "transports":["internal"]},"clientExtensionResults":{}}
        """.trimIndent()
    }

    private fun attestedAuthenticatorData(): ByteArray {
        val credentialIdLength = ByteBuffer.allocate(2).putShort(credentialId.size.toShort()).array()
        return rpIdHash() + byteArrayOf(ATTESTED_USER_PRESENT_AND_VERIFIED) + counter(0) + ByteArray(AAGUID_LENGTH) +
            credentialIdLength + credentialId + cosePublicKey
    }

    private fun clientDataJson(type: String, challenge: String, origin: String): ByteArray {
        return """{"type":"$type","challenge":"$challenge","origin":"$origin","crossOrigin":false}"""
            .encodeToByteArray()
    }

    private fun rpIdHash(): ByteArray {
        return sha256(AppLevelConstants.domainName.encodeToByteArray())
    }

    private fun counter(value: Int): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()
    }

    private fun noneAttestationOf(authenticatorData: ByteArray): ByteArray {
        return ByteArrayOutputStream().apply {
            write(CBOR_MAP_OF_THREE)
            writeText("fmt")
            writeText("none")
            writeText("attStmt")
            write(CBOR_EMPTY_MAP)
            writeText("authData")
            write(CBOR_BYTES_WITH_TWO_BYTE_LENGTH)
            write(ByteBuffer.allocate(2).putShort(authenticatorData.size.toShort()).array())
            write(authenticatorData)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeText(text: String) {
        write(CBOR_SHORT_TEXT + text.length)
        write(text.encodeToByteArray())
    }

    companion object {
        private const val CREDENTIAL_ID_LENGTH = 16
        private const val AAGUID_LENGTH = 16
        private const val COORDINATE_LENGTH = 32
        private const val USER_PRESENT_AND_VERIFIED: Byte = 0x05
        private const val ATTESTED_USER_PRESENT_AND_VERIFIED: Byte = 0x45
        private const val CBOR_MAP_OF_THREE = 0xA3
        private const val CBOR_EMPTY_MAP = 0xA0
        private const val CBOR_SHORT_TEXT = 0x60
        private const val CBOR_BYTES_WITH_TWO_BYTE_LENGTH = 0x59

        fun newKeyPair(): KeyPair {
            return KeyPairGenerator.getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair()
        }

        fun encode(bytes: ByteArray): String {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        private fun sha256(bytes: ByteArray): ByteArray {
            return MessageDigest.getInstance("SHA-256").digest(bytes)
        }

        private fun sign(keyPair: KeyPair, data: ByteArray): ByteArray {
            return Signature.getInstance("SHA256withECDSA").run {
                initSign(keyPair.private)
                update(data)
                sign()
            }
        }

        private fun coseKeyOf(publicKey: ECPublicKey): ByteArray {
            val header = byteArrayOf(0xA5.toByte(), 0x01, 0x02, 0x03, 0x26, 0x20, 0x01)
            val x = byteArrayOf(0x21, 0x58, COORDINATE_LENGTH.toByte()) + coordinate(publicKey.w.affineX)
            val y = byteArrayOf(0x22, 0x58, COORDINATE_LENGTH.toByte()) + coordinate(publicKey.w.affineY)
            return header + x + y
        }

        private fun coordinate(value: BigInteger): ByteArray {
            val bytes = value.toByteArray().takeLast(COORDINATE_LENGTH).toByteArray()
            return ByteArray(COORDINATE_LENGTH - bytes.size) + bytes
        }
    }
}

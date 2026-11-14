package library.outbox.personal

import com.bookk.core.data.eventstreaming.PersonalEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import library.encryption.Decrypted
import library.encryption.MissingKeys
import library.encryption.SubjectKeys
import kotlin.uuid.Uuid

class SubjectKeysPersonalDataCipher(
    private val subjectKeys: SubjectKeys,
    private val clientKey: ClientKey,
    private val missingKeys: MissingKeys,
) : PersonalDataCipher {

    override suspend fun seal(event: PersonalEvent, payload: ByteArray): ByteArray? {
        val keyId = clientKey.keyIdOf(event.dataSubject)
        val encryptor = subjectKeys.encryptor(keyId) ?: return null
        val envelope = SealedPayload(keyId = keyId, ciphertext = encryptor.encrypt(payload))
        return ProtoBuf.encodeToByteArray(SealedPayload.serializer(), envelope)
    }

    override suspend fun open(topic: String, sealed: ByteArray): ByteArray? {
        val envelope = envelopeOf(sealed)
        val decryptor = subjectKeys.decryptor(listOf(envelope.keyId))
        val decrypted = runCatching { decryptor.decrypt(envelope.keyId, envelope.ciphertext) }
            .getOrElse { throw UnreadableSealedPayload("The sealed payload of $topic failed authentication", it) }
        return when (decrypted) {
            is Decrypted.Plain -> decrypted.value

            Decrypted.Shredded -> null

            Decrypted.Missing -> {
                missingKeys.report(topic)
                null
            }
        }
    }

    private fun envelopeOf(sealed: ByteArray): SealedPayload {
        return runCatching { ProtoBuf.decodeFromByteArray(SealedPayload.serializer(), sealed) }
            .getOrElse { throw UnreadableSealedPayload("Not a sealed payload", it) }
    }
}

@Serializable
private class SealedPayload(
    @ProtoNumber(1) val keyId: Uuid,
    @ProtoNumber(2) val ciphertext: ByteArray,
)

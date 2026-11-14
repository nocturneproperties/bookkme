package library.outbox.personal

import com.bookk.core.data.eventstreaming.PersonalEvent
import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import library.encryption.Decrypted
import library.encryption.SubjectKeys
import kotlin.uuid.Uuid

class SubjectKeysPersonalDataCipher(
    private val subjectKeys: SubjectKeys,
    private val clientKey: ClientKey,
    private val meterRegistry: MeterRegistry,
) : PersonalDataCipher {

    private val logger = KtorSimpleLogger("SubjectKeysPersonalDataCipher")

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
                reportMissingKey(topic)
                null
            }
        }
    }

    private fun envelopeOf(sealed: ByteArray): SealedPayload {
        return runCatching { ProtoBuf.decodeFromByteArray(SealedPayload.serializer(), sealed) }
            .getOrElse { throw UnreadableSealedPayload("Not a sealed payload", it) }
    }

    private fun reportMissingKey(topic: String) {
        logger.error("Dropped event {} because its data subject's key is missing although it was never erased", topic)
        Counter.builder(KEY_MISSING_METRIC)
            .description("Events dropped because a data subject's key is missing from the key schema")
            .tag(TOPIC_TAG, topic)
            .register(meterRegistry)
            .increment()
    }

    companion object {
        const val KEY_MISSING_METRIC = "personal_data.key_missing"
        private const val TOPIC_TAG = "topic"
    }
}

@Serializable
private class SealedPayload(
    @ProtoNumber(1) val keyId: Uuid,
    @ProtoNumber(2) val ciphertext: ByteArray,
)

package library.outbox.personal

import com.bookk.core.data.eventstreaming.DataSubject
import com.bookk.core.data.eventstreaming.PersonalEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.util.concurrent.ConcurrentHashMap

@Serializable
private class MarkedPayload(
    @ProtoNumber(1) val subject: String,
    @ProtoNumber(2) val payload: ByteArray,
)

class MarkingPersonalDataCipher : PersonalDataCipher {
    private val erased = ConcurrentHashMap.newKeySet<String>()

    fun erase(subject: DataSubject) {
        erased += subject.toString()
    }

    override suspend fun seal(event: PersonalEvent, payload: ByteArray): ByteArray? {
        if (event.dataSubject.toString() in erased) return null
        return sealed(event.dataSubject, payload)
    }

    override suspend fun open(topic: String, sealed: ByteArray): ByteArray? {
        val marked = marked(sealed)
        if (marked.subject in erased) return null
        return marked.payload
    }

    companion object {
        fun sealed(subject: DataSubject, payload: ByteArray): ByteArray {
            return ProtoBuf.encodeToByteArray(MarkedPayload.serializer(), MarkedPayload(subject.toString(), payload))
        }

        fun plaintextOf(sealed: ByteArray): ByteArray {
            return marked(sealed).payload
        }

        private fun marked(sealed: ByteArray): MarkedPayload {
            return runCatching { ProtoBuf.decodeFromByteArray(MarkedPayload.serializer(), sealed) }
                .getOrElse { throw UnreadableSealedPayload("Not a marked payload", it) }
        }
    }
}

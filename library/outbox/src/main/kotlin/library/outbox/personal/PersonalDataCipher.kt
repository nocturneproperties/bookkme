package library.outbox.personal

import com.bookk.core.data.eventstreaming.EventStreaming.Event
import com.bookk.core.data.eventstreaming.PersonalEvent

interface PersonalDataCipher {
    suspend fun seal(event: PersonalEvent, payload: ByteArray): ByteArray?

    suspend fun open(topic: String, sealed: ByteArray): ByteArray?
}

class UnreadableSealedPayload(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

class StoredPayload(val payload: ByteArray, val personalDataEncrypted: Boolean)

suspend fun PersonalDataCipher.storedPayload(event: Event<String>, payload: ByteArray): StoredPayload? {
    if (event !is PersonalEvent) return StoredPayload(payload, personalDataEncrypted = false)
    val sealed = seal(event, payload) ?: return null
    return StoredPayload(sealed, personalDataEncrypted = true)
}

suspend fun PersonalDataCipher.readablePayload(topic: String, stored: StoredPayload): ByteArray? {
    if (!stored.personalDataEncrypted) return stored.payload
    return open(topic, stored.payload)
}

object NoPersonalDataCipher : PersonalDataCipher {
    override suspend fun seal(event: PersonalEvent, payload: ByteArray): ByteArray? {
        throw refusal(event.topic)
    }

    override suspend fun open(topic: String, sealed: ByteArray): ByteArray? {
        throw refusal(topic)
    }

    private fun refusal(topic: String): IllegalStateException {
        return IllegalStateException("Event $topic carries personal data but this service has no keys")
    }
}

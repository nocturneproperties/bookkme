package library.outbox.personal

import com.bookk.core.data.eventstreaming.DataSubject
import kotlin.uuid.Uuid

enum class ClientKey {
    CLIENT_ID,
    LINKED_USER_ID,
    ;

    fun keyIdOf(subject: DataSubject): Uuid {
        return when (subject) {
            is DataSubject.User -> subject.userId
            is DataSubject.Client -> clientKeyId(subject)
        }
    }

    private fun clientKeyId(client: DataSubject.Client): Uuid {
        return when (this) {
            CLIENT_ID -> client.clientId
            LINKED_USER_ID -> client.userId ?: client.clientId
        }
    }
}

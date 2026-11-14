package library.outbox

import com.bookk.core.data.eventstreaming.EventStreaming
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import library.outbox.datasource.OutboxEventDataSource
import library.outbox.personal.PersonalDataCipher
import library.outbox.personal.storedPayload
import kotlin.reflect.KType

class OutboxEventProducer(
    private val outboxEventDataSource: OutboxEventDataSource,
    private val transactionManager: TransactionManager,
    private val protoBuf: ProtoBuf,
    private val personalDataCipher: PersonalDataCipher,
) : EventStreaming.Producer<String> {

    override suspend fun <T : EventStreaming.Event<String>> send(data: T, kType: KType) {
        check(transactionManager.isInTransaction()) {
            "Event ${data.topic} must be sent inside a transaction so it commits or rolls back with it"
        }
        val payload = protoBuf.encodeToByteArray(protoBuf.serializersModule.serializer(kType), data)
        val stored = checkNotNull(personalDataCipher.storedPayload(data, payload)) {
            "Event ${data.topic} carries personal data of an erased data subject"
        }
        outboxEventDataSource.enqueue(
            topic = data.topic,
            partitionKey = data.partitionKey,
            payload = stored.payload,
            personalDataEncrypted = stored.personalDataEncrypted,
        )
    }
}

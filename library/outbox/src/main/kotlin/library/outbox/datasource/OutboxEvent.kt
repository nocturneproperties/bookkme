package library.outbox.datasource

class OutboxEvent(
    val id: Long,
    val topic: String,
    val partitionKey: String?,
    val payload: ByteArray,
    val personalDataEncrypted: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OutboxEvent) return false
        return id == other.id &&
            topic == other.topic &&
            partitionKey == other.partitionKey &&
            payload.contentEquals(other.payload) &&
            personalDataEncrypted == other.personalDataEncrypted
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + topic.hashCode()
        result = 31 * result + (partitionKey?.hashCode() ?: 0)
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + personalDataEncrypted.hashCode()
        return result
    }

    companion object {
        fun stub(
            id: Long = 1,
            topic: String = "topic",
            partitionKey: String? = null,
            payload: ByteArray = byteArrayOf(),
            personalDataEncrypted: Boolean = false,
        ) = OutboxEvent(
            id = id,
            topic = topic,
            partitionKey = partitionKey,
            payload = payload,
            personalDataEncrypted = personalDataEncrypted,
        )
    }
}

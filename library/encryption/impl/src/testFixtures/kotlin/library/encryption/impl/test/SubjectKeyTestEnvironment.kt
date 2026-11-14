package library.encryption.impl.test

import kotlin.io.encoding.Base64

val subjectKeyTestEnvironment: Map<String, String> = mapOf(
    "APPLICATION_SUBJECT_MASTER_KEY" to Base64.encode(ByteArray(32)),
    "APPLICATION_BLIND_INDEX_KEY" to Base64.encode(ByteArray(32)),
)

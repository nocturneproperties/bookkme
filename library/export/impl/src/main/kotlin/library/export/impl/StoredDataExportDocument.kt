package library.export.impl

import kotlin.uuid.Uuid

internal class StoredDataExportDocument(val ciphertext: ByteArray, val keyId: Uuid?)

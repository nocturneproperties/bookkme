package com.bookk.business.data.orm.entity

import kotlin.uuid.Uuid

internal data class Contact(
    val name: String,
    val lastName: String,
    val phone: String?,
    val email: String?,
    val description: String? = null,
    val telegramUsername: String? = null,
) {
    companion object {
        val UNREADABLE = Contact(name = "", lastName = "", phone = null, email = null)
    }
}

internal class EncryptedContact(
    val name: ByteArray,
    val lastName: ByteArray,
    val phone: ByteArray?,
    val email: ByteArray?,
    val description: ByteArray? = null,
    val telegramUsername: ByteArray? = null,
)

internal class StoredContact(val subjectId: Uuid, val encrypted: EncryptedContact)

internal class ContactIndexes(val email: String?, val phone: String?, val nameTokens: List<ByteArray>)

internal class ContactReindex(val email: Boolean, val name: Boolean, val phone: Boolean)

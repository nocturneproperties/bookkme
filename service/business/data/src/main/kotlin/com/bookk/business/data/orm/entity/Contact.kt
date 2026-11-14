package com.bookk.business.data.orm.entity

internal data class Contact(
    val name: String,
    val lastName: String,
    val phone: String?,
    val email: String?,
    val description: String? = null,
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
)

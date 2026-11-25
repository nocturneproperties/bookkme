package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.Contact

internal class ClientSearch(
    val words: List<String>,
    val wordTokens: List<List<ByteArray>>,
    val emailIndex: String,
    val phoneIndex: String?,
) {
    fun matches(storedEmailIndex: String?, storedPhoneIndex: String?, contact: Contact): Boolean {
        val emailMatches = emailIndex == storedEmailIndex
        val phoneMatches = phoneIndex != null && phoneIndex == storedPhoneIndex
        return emailMatches || phoneMatches || namesMatch(contact)
    }

    private fun namesMatch(contact: Contact): Boolean {
        if (wordTokens.isEmpty()) return false
        val nameWords = NameWords.of(contact.name) + NameWords.of(contact.lastName)
        return NameWords.allStartWords(words, nameWords)
    }
}

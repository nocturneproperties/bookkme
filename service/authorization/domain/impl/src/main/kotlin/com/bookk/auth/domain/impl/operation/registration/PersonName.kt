package com.bookk.auth.domain.impl.operation.registration

internal data class PersonName(val name: String, val lastName: String) {

    val fullName: String
        get() {
            return listOf(name, lastName).filter { it.isNotEmpty() }.joinToString(" ")
        }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        fun of(fullName: String): PersonName {
            val words = fullName.split(WHITESPACE).filter { it.isNotEmpty() }
            return PersonName(
                name = words.firstOrNull().orEmpty(),
                lastName = words.drop(1).joinToString(" "),
            )
        }
    }
}

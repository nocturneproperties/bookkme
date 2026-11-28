package com.bookk.auth.domain.api.telegram.entity

data class TelegramUser(
    val id: Long,
    val firstName: String,
    val lastName: String?,
    val username: String?,
    val usernameRejected: Boolean = false,
) {
    companion object {
        fun stub(
            id: Long = 123456789,
            firstName: String = "Ann",
            lastName: String? = "Lee",
            username: String? = "ann_lee",
            usernameRejected: Boolean = false,
        ): TelegramUser {
            return TelegramUser(id, firstName, lastName, username, usernameRejected)
        }
    }
}

package com.bookk.user.domain.api.entity

data class TelegramUsernameChange(
    val user: User,
    val changed: Boolean,
) {
    companion object {
        fun stub(user: User = User.stub(), changed: Boolean = true): TelegramUsernameChange {
            return TelegramUsernameChange(user, changed)
        }
    }
}

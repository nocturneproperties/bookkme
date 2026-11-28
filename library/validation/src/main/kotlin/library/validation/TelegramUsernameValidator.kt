package library.validation

object TelegramUsernameValidator {
    private val telegramUsername = Regex("[A-Za-z0-9_]{4,32}")

    fun isValid(username: String): Boolean {
        return telegramUsername.matches(username)
    }
}

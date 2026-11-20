package library.email

object PlaceholderEmail {
    const val DOMAIN = "telegramundeliverable.org"

    fun forTelegramUser(telegramUserId: Long): String {
        return "$telegramUserId@$DOMAIN"
    }

    fun isPlaceholder(address: String): Boolean {
        val domain = address.trim().lowercase().substringAfterLast('@')
        return domain == DOMAIN || domain.endsWith(".$DOMAIN")
    }
}

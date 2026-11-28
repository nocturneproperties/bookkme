package library.email

import kotlin.uuid.Uuid

object PlaceholderEmail {
    const val DOMAIN = "telegramundeliverable.org"
    const val DEMO_DOMAIN = "demoundeliverable.org"

    private val DOMAINS = listOf(DOMAIN, DEMO_DOMAIN)

    fun forTelegramUser(telegramUserId: Long): String {
        return "$telegramUserId@$DOMAIN"
    }

    fun forDemoUser(placeholderId: Uuid): String {
        return "${placeholderId.toHexString()}@$DEMO_DOMAIN"
    }

    fun isPlaceholder(address: String): Boolean {
        val domain = address.trim().lowercase().substringAfterLast('@')
        return DOMAINS.any { domain == it || domain.endsWith(".$it") }
    }
}

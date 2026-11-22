package com.bookk.business.domain.impl.operation.business

import kotlin.time.Duration.Companion.days

internal object BusinessLinkPolicy {
    val HOLD_PERIOD = 90.days
    const val MAX_CHANGES = 3

    private val NAME_PATTERN = Regex("^(?=.{3,32}$)[a-z0-9]+(-[a-z0-9]+)*$")
    private val RESERVED_NAMES = setOf(
        "about", "abuse", "account", "admin", "api", "app", "apps", "assets", "auth", "billing", "blog", "book",
        "booking", "bookk", "bookkme", "business", "businesses", "careers", "cdn", "contact", "contacts", "cookies",
        "dashboard", "delete", "dev", "docs", "download", "email", "en", "faq", "help", "home", "imprint", "index",
        "info", "invite", "legal", "login", "logout", "mail", "news", "pl", "postmaster", "press", "pricing",
        "privacy", "register", "root", "security", "settings", "sign-in", "sign-up", "signin", "signup", "site",
        "static", "status", "support", "telegram", "terms", "test", "uk", "webmaster", "well-known", "www",
    )

    fun normalize(name: String): String {
        return name.trim().lowercase()
    }

    fun isValid(name: String): Boolean {
        return NAME_PATTERN.matches(name)
    }

    fun isReserved(name: String): Boolean {
        return name in RESERVED_NAMES
    }
}

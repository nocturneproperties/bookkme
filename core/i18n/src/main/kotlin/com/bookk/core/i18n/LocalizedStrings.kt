package com.bookk.core.i18n

import com.bookk.core.domain.entity.Language
import com.ibm.icu.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

fun formatPlural(pattern: String, locale: Locale, arguments: Map<String, Any>): String =
    MessageFormat(pattern, locale).format(arguments)

class LocalizedStrings(baseName: String) {

    private val bundles: Map<Language, ResourceBundle> = Language.entries.associateWith { language ->
        ResourceBundle.getBundle(baseName, language.toLocale())
    }

    fun message(language: Language, key: String, vararg args: Any): String {
        val template = bundles.getValue(language).getString(key)
        return if (args.isEmpty()) template else String.format(language.toLocale(), template, *args)
    }

    fun pluralMessage(language: Language, key: String, count: Int, vararg args: Pair<String, Any>): String {
        val pattern = bundles.getValue(language).getString(key)
        return formatPlural(pattern, language.toLocale(), mapOf("count" to count, *args))
    }
}

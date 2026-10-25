package com.bookk.core.i18n

import com.bookk.core.domain.entity.Language
import kotlin.test.Test
import kotlin.test.assertEquals

internal class LocalizedStringsTest {

    private val messages = LocalizedStrings("i18n.test-messages")

    @Test
    fun `should format a positional message in English`() {
        assertEquals("Hello, Alice!", messages.message(Language.EN, "greeting", "Alice"))
    }

    @Test
    fun `should format a positional message in Ukrainian`() {
        assertEquals("Привіт, Alice!", messages.message(Language.UK, "greeting", "Alice"))
    }

    @Test
    fun `should resolve English singular plural form via a resource bundle key`() {
        assertEquals("You have 1 appointment tomorrow", messages.pluralMessage(Language.EN, "appointment.count", 1))
    }

    @Test
    fun `should resolve English plural form via a resource bundle key`() {
        assertEquals("You have 5 appointments tomorrow", messages.pluralMessage(Language.EN, "appointment.count", 5))
    }

    @Test
    fun `should resolve Ukrainian few form via a resource bundle key`() {
        assertEquals("У вас 3 записи завтра", messages.pluralMessage(Language.UK, "appointment.count", 3))
    }

    @Test
    fun `should resolve Ukrainian many form via a resource bundle key`() {
        assertEquals("У вас 11 записів завтра", messages.pluralMessage(Language.UK, "appointment.count", 11))
    }

    @Test
    fun `should format plural pattern directly using formatPlural`() {
        val pattern = "{count, plural, one {# day} other {# days}}"

        assertEquals("1 day", formatPlural(pattern, Language.EN.toLocale(), mapOf("count" to 1)))
        assertEquals("7 days", formatPlural(pattern, Language.EN.toLocale(), mapOf("count" to 7)))
    }

    @Test
    fun `should map Language to the expected Locale`() {
        assertEquals("en", Language.EN.toLocale().language)
        assertEquals("uk", Language.UK.toLocale().language)
    }
}

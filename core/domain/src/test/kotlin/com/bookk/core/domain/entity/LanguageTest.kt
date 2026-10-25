package com.bookk.core.domain.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LanguageTest {

    @Test
    fun `should resolve UK from a header naming Ukrainian first`() {
        assertEquals(Language.UK, Language.fromAcceptLanguage("uk-UA,uk;q=0.9,en;q=0.8"))
    }

    @Test
    fun `should resolve EN from a header naming English first`() {
        assertEquals(Language.EN, Language.fromAcceptLanguage("en-US,en;q=0.9"))
    }

    @Test
    fun `should fall back to EN when header is null`() {
        assertEquals(Language.EN, Language.fromAcceptLanguage(null))
    }

    @Test
    fun `should fall back to EN when header is blank`() {
        assertEquals(Language.EN, Language.fromAcceptLanguage("   "))
    }

    @Test
    fun `should fall back to EN when header names an unsupported language`() {
        assertEquals(Language.EN, Language.fromAcceptLanguage("fr-FR,fr;q=0.9"))
    }
}

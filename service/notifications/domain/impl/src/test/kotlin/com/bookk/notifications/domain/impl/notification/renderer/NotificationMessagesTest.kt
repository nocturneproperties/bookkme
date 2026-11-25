package com.bookk.notifications.domain.impl.notification.renderer

import com.bookk.core.domain.entity.Language
import com.bookk.core.i18n.toLocale
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties

internal class NotificationMessagesTest {

    private fun keysOf(resource: String): Set<String> {
        return propertiesOf(resource).stringPropertyNames()
    }

    private fun propertiesOf(resource: String): Properties {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(resource)) { "Missing $resource" }
        return stream.reader(Charsets.UTF_8).use { reader -> Properties().apply { load(reader) } }
    }

    private fun bundleOf(language: Language): String = when (language) {
        Language.EN -> "i18n/notifications.properties"
        else -> "i18n/notifications_${language.toLocale().language}.properties"
    }

    @Test
    fun `should translate every notification message into every language`() = runUnitTest {
        given()
        val baseKeys = keysOf(bundleOf(Language.EN))

        whenn()
        val keysByLanguage = Language.entries.associateWith { keysOf(bundleOf(it)) }

        then()
        keysByLanguage.forEach { (language, keys) -> assertEquals(baseKeys, keys, "$language") }
    }

    @Test
    fun `should call the service BookkMe in every notification message`() = runUnitTest {
        given()
        val shortName = Regex("Bookk(?!Me)")

        whenn()
        val messages = Language.entries.flatMap { language ->
            val properties = propertiesOf(bundleOf(language))
            properties.stringPropertyNames().map { key -> "$language $key" to properties.getProperty(key) }
        }

        then()
        val misnamed = messages.filter { (_, message) -> shortName.containsMatchIn(message) }.map { it.first }
        assertTrue(misnamed.isEmpty()) { misnamed.toString() }
    }

    @Test
    fun `should call the account акаунт in every Ukrainian notification message`() = runUnitTest {
        given()
        val properties = propertiesOf(bundleOf(Language.UK))

        whenn()
        val misworded = properties.stringPropertyNames().filter { key ->
            properties.getProperty(key).contains("обліков", ignoreCase = true)
        }

        then()
        assertTrue(misworded.isEmpty()) { misworded.toString() }
    }
}

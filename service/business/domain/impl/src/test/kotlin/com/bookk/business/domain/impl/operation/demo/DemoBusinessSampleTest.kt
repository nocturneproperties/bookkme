package com.bookk.business.domain.impl.operation.demo

import com.bookk.core.domain.entity.DemoBusinessKind
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

internal class DemoBusinessSampleTest {

    @Test
    fun `should offer a kind the same services at the same durations and prices in every language`() = runUnitTest {
        given()
        val kinds = DemoBusinessKind.entries

        whenn()
        val shapes = kinds.associateWith { kind ->
            Language.entries.map { language ->
                DemoBusinessSample.of(kind, language).groups.map { group ->
                    group.services.map { it.duration to it.priceInEuro }
                }
            }
        }

        then()
        shapes.forEach { (kind, byLanguage) ->
            assertEquals(1, byLanguage.toSet().size) { kind.name }
        }
    }

    @Test
    fun `should name every kind, group and service distinctly in every language`() = runUnitTest {
        given()
        val samples = DemoBusinessKind.entries.flatMap { kind ->
            Language.entries.map { language -> "$kind $language" to DemoBusinessSample.of(kind, language) }
        }

        whenn()
        val businessNamesByLanguage = Language.entries.map { language ->
            DemoBusinessKind.entries.map { DemoBusinessSample.of(it, language).name }
        }

        then()
        businessNamesByLanguage.forEach { names -> assertEquals(names.size, names.toSet().size) { names.toString() } }
        samples.forEach { (label, sample) ->
            val serviceNames = sample.groups.flatMap { group -> group.services.map { it.name } }
            assertTrue(sample.groups.all { it.name.isNotBlank() }) { label }
            assertEquals(serviceNames.size, serviceNames.filter { it.isNotBlank() }.toSet().size) { label }
        }
    }

    @Test
    fun `should keep every sample service within an hour so it fits the sample bookings`() = runUnitTest {
        given()
        val kinds = DemoBusinessKind.entries

        whenn()
        val durations = kinds.flatMap { kind ->
            DemoBusinessSample.of(kind, Language.EN).groups.flatMap { group -> group.services.map { it.duration } }
        }

        then()
        assertTrue(durations.all { it in 15.minutes..60.minutes }) { durations.toString() }
    }

    @Test
    fun `should give every language ten clients with distinct plain example dot com emails`() = runUnitTest {
        given()
        val plainEmail = Regex("^[a-z]+\\.[a-z]+@example\\.com$")

        whenn()
        val clientsByLanguage = Language.entries.associateWith {
            DemoBusinessSample.of(DemoBusinessKind.GENERAL, it).clients()
        }

        then()
        clientsByLanguage.forEach { (language, clients) ->
            val emails = clients.map { it.email.orEmpty() }
            assertEquals(10, clients.size) { language.name }
            assertEquals(emails.size, emails.toSet().size) { language.name }
            assertTrue(emails.all(plainEmail::matches)) { "$language $emails" }
        }
    }
}

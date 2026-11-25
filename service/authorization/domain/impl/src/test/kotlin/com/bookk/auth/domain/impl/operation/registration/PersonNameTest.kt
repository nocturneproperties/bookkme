package com.bookk.auth.domain.impl.operation.registration

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PersonNameTest {

    @Test
    fun `should split the full name into the first word and the rest`() = runUnitTest {
        given()
        val fullName = "John Doe"

        whenn()
        val result = PersonName.of(fullName)

        then()
        assertEquals(PersonName(name = "John", lastName = "Doe"), result)
    }

    @Test
    fun `should leave the last name empty when the full name is a single word`() = runUnitTest {
        given()
        val fullName = "John"

        whenn()
        val result = PersonName.of(fullName)

        then()
        assertEquals(PersonName(name = "John", lastName = ""), result)
    }

    @Test
    fun `should keep a one letter last name`() = runUnitTest {
        given()
        val fullName = "John D"

        whenn()
        val result = PersonName.of(fullName)

        then()
        assertEquals(PersonName(name = "John", lastName = "D"), result)
    }

    @Test
    fun `should keep every word after the first in the last name`() = runUnitTest {
        given()
        val fullName = "Juan Carlos de la Cruz"

        whenn()
        val result = PersonName.of(fullName)

        then()
        assertEquals(PersonName(name = "Juan", lastName = "Carlos de la Cruz"), result)
    }

    @Test
    fun `should trim the name and collapse the whitespace between words`() = runUnitTest {
        given()
        val fullName = "  John \t  Van\n Doe  "

        whenn()
        val result = PersonName.of(fullName)

        then()
        assertEquals(PersonName(name = "John", lastName = "Van Doe"), result)
    }

    @Test
    fun `should return empty parts for a blank name`() = runUnitTest {
        given()
        val fullName = "   "

        whenn()
        val result = PersonName.of(fullName)

        then()
        assertEquals(PersonName(name = "", lastName = ""), result)
    }

    @Test
    fun `should join the parts back into the full name`() = runUnitTest {
        given()
        val names = listOf(PersonName(name = "John", lastName = "Doe"), PersonName(name = "John", lastName = ""))

        whenn()
        val result = names.map { it.fullName }

        then()
        assertEquals(listOf("John Doe", "John"), result)
    }
}

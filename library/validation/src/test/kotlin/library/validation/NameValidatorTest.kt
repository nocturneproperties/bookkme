package library.validation

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class NameValidatorTest {

    @Test
    fun `should accept a name within the default bounds`() = runUnitTest {
        given()
        val name = "John"

        whenn()
        val result = NameValidator.isValid(name)

        then()
        assertTrue(result)
    }

    @Test
    fun `should reject a name longer than the max length`() = runUnitTest {
        given()
        val name = "a".repeat(513)

        whenn()
        val result = NameValidator.isValid(name)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject a blank name when min length is enforced`() = runUnitTest {
        given()
        val name = ""

        whenn()
        val result = NameValidator.isValid(name, minLength = 2)

        then()
        assertFalse(result)
    }

    @Test
    fun `should accept a blank name when min length is not enforced`() = runUnitTest {
        given()
        val name = ""

        whenn()
        val result = NameValidator.isValid(name, minLength = 0)

        then()
        assertTrue(result)
    }

    @Test
    fun `should honor a custom max length`() = runUnitTest {
        given()
        val name = "John"

        whenn()
        val result = NameValidator.isValid(name, maxLength = 3)

        then()
        assertFalse(result)
    }
}

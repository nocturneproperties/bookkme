package library.validation

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EmailValidatorTest {

    @Test
    fun `should accept a well formed email`() = runUnitTest {
        given()
        val email = "alice@test.com"

        whenn()
        val result = EmailValidator.isValid(email)

        then()
        assertTrue(result)
    }

    @Test
    fun `should reject a blank email`() = runUnitTest {
        given()
        val email = " "

        whenn()
        val result = EmailValidator.isValid(email)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject an email without an at sign`() = runUnitTest {
        given()
        val email = "not-an-email"

        whenn()
        val result = EmailValidator.isValid(email)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject an email longer than the max length`() = runUnitTest {
        given()
        val email = "${"a".repeat(513)}@test.com"

        whenn()
        val result = EmailValidator.isValid(email)

        then()
        assertFalse(result)
    }

    @Test
    fun `should honor a custom max length`() = runUnitTest {
        given()
        val email = "alice@test.com"

        whenn()
        val result = EmailValidator.isValid(email, maxLength = 5)

        then()
        assertFalse(result)
    }
}

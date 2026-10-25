package library.validation

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PhoneValidatorTest {

    @Test
    fun `should accept a plain digit phone number`() = runUnitTest {
        given()
        val phone = "123456"

        whenn()
        val result = PhoneValidator.isValid(phone)

        then()
        assertTrue(result)
    }

    @Test
    fun `should accept an international phone number`() = runUnitTest {
        given()
        val phone = "+1 (415) 555-2671"

        whenn()
        val result = PhoneValidator.isValid(phone)

        then()
        assertTrue(result)
    }

    @Test
    fun `should reject a phone number without any digits`() = runUnitTest {
        given()
        val phone = "+ ()-"

        whenn()
        val result = PhoneValidator.isValid(phone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject a phone number containing letters`() = runUnitTest {
        given()
        val phone = "call-me-maybe"

        whenn()
        val result = PhoneValidator.isValid(phone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject a phone number shorter than the min length`() = runUnitTest {
        given()
        val phone = "12"

        whenn()
        val result = PhoneValidator.isValid(phone)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject a phone number longer than the max length`() = runUnitTest {
        given()
        val phone = "1".repeat(33)

        whenn()
        val result = PhoneValidator.isValid(phone)

        then()
        assertFalse(result)
    }
}

package library.validation

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CurrencyValidatorTest {

    @Test
    fun `should accept an ISO 4217 currency code`() = runUnitTest {
        given()
        val currencyCode = "UAH"

        whenn()
        val result = CurrencyValidator.isValid(currencyCode)

        then()
        assertTrue(result)
    }

    @Test
    fun `should reject an unknown currency code`() = runUnitTest {
        given()
        val currencyCode = "ABC"

        whenn()
        val result = CurrencyValidator.isValid(currencyCode)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject a lowercase currency code`() = runUnitTest {
        given()
        val currencyCode = "usd"

        whenn()
        val result = CurrencyValidator.isValid(currencyCode)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject a currency code longer than three letters`() = runUnitTest {
        given()
        val currencyCode = "USDD"

        whenn()
        val result = CurrencyValidator.isValid(currencyCode)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject an empty currency code`() = runUnitTest {
        given()
        val currencyCode = ""

        whenn()
        val result = CurrencyValidator.isValid(currencyCode)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject the no-currency code XXX`() = runUnitTest {
        given()
        val currencyCode = "XXX"

        whenn()
        val result = CurrencyValidator.isValid(currencyCode)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject a precious metal code XAU`() = runUnitTest {
        given()
        val currencyCode = "XAU"

        whenn()
        val result = CurrencyValidator.isValid(currencyCode)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject the testing code XTS`() = runUnitTest {
        given()
        val currencyCode = "XTS"

        whenn()
        val result = CurrencyValidator.isValid(currencyCode)

        then()
        assertFalse(result)
    }

    @Test
    fun `should reject a special drawing rights code XDR`() = runUnitTest {
        given()
        val currencyCode = "XDR"

        whenn()
        val result = CurrencyValidator.isValid(currencyCode)

        then()
        assertFalse(result)
    }

    @Test
    fun `should accept a real currency whose code starts with X`() = runUnitTest {
        given()
        val currencyCode = "XOF"

        whenn()
        val result = CurrencyValidator.isValid(currencyCode)

        then()
        assertTrue(result)
    }
}

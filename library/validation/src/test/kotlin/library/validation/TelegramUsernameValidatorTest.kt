package library.validation

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TelegramUsernameValidatorTest {

    @Test
    fun `should accept letters, digits and underscores from 4 to 32 characters`() = runUnitTest {
        given()
        val usernames = listOf("Ann1", "ann_lee", "a".repeat(32))

        whenn()
        val results = usernames.map(TelegramUsernameValidator::isValid)

        then()
        assertEquals(listOf(true, true, true), results)
    }

    @Test
    fun `should reject a username that is too short, too long or has other characters`() = runUnitTest {
        given()
        val usernames = listOf("", "ann", "a".repeat(33), "ann lee", "ann-lee", "@ann_lee", "анна_лі")

        whenn()
        val results = usernames.map(TelegramUsernameValidator::isValid)

        then()
        assertEquals(usernames.map { false }, results)
    }
}

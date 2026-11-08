package library.email.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.util.Random

internal class EmailCodeTest {

    private class DigitSequenceRandom(digits: List<Int>) : Random() {
        private val remaining = digits.iterator()
        val bounds = mutableListOf<Int>()

        override fun nextInt(bound: Int): Int {
            bounds += bound
            return remaining.next()
        }
    }

    @Test
    fun `should draw one decimal digit per code position`() = runUnitTest {
        given()
        val random = DigitSequenceRandom(listOf(0, 0, 0, 0, 4, 2))

        whenn()
        val code = newEmailCode(random)

        then()
        assertEquals("000042", code)
        assertEquals(List(6) { 10 }, random.bounds)
    }

    @Test
    fun `should draw six digit codes`() = runUnitTest {
        given()
        val random = SecureRandom()

        whenn()
        val codes = List(1_000) { newEmailCode(random) }

        then()
        assertTrue(codes.all { it.matches(Regex("\\d{6}")) }) { codes.toString() }
    }
}

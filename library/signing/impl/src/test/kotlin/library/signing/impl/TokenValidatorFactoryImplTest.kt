package library.signing.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.signing.TokenAudience
import library.signing.ValidationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TokenValidatorFactoryImplTest {

    @Test
    fun `should verify every token type against an audience no other type uses`() = runUnitTest {
        given()
        val types = ValidationType.entries

        whenn()
        val audiences = types.map { it.audience }

        then()
        assertEquals(types.size, audiences.distinct().size)
    }

    @Test
    fun `should verify service quotes against the service quote audience`() = runUnitTest {
        given()
        val type = ValidationType.SERVICE_QUOTE

        whenn()
        val audience = type.audience

        then()
        assertEquals(TokenAudience.serviceQuote, audience)
    }
}

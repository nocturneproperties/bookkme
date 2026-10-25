package com.bookk.core.client

import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClientResponseTest {

    @Test
    fun `should preserve the error code when the service returns a business error`() = runUnitTest {
        given()
        val serverError = SimpleServerError(errorCode = 200003, message = "Business with this id is missing")

        whenn()
        val error = domainErrorOf(HttpStatusCode.NotFound, serverError, cause = null)

        then()
        assertTrue(error is BusinessError)
        assertEquals(200003, (error as BusinessError).code)
        assertEquals(HttpStatusCode.NotFound.value, error.statusCode)
        assertEquals("Business with this id is missing", error.message)
    }

    @Test
    fun `should preserve the status code of an unprocessable entity error`() = runUnitTest {
        given()
        val serverError = SimpleServerError(errorCode = 300009, message = "Appointment plugin already enabled")

        whenn()
        val error = domainErrorOf(HttpStatusCode.UnprocessableEntity, serverError, cause = null)

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity.value, (error as BusinessError).statusCode)
        assertEquals(300009, error.code)
    }

    @Test
    fun `should map an undecodable not found to the domain not found error`() = runUnitTest {
        given()
        val cause = IllegalStateException("no protobuf body")

        whenn()
        val error = domainErrorOf(HttpStatusCode.NotFound, serverError = null, cause = cause)

        then()
        assertTrue(error is Error.NotFound)
    }

    @Test
    fun `should map an undecodable forbidden to the operation not allowed error`() = runUnitTest {
        given()
        val cause = IllegalStateException("no protobuf body")

        whenn()
        val error = domainErrorOf(HttpStatusCode.Forbidden, serverError = null, cause = cause)

        then()
        assertTrue(error is Error.OperationNotAllowed)
    }

    @Test
    fun `should map an undecodable server failure to an unknown error`() = runUnitTest {
        given()
        val cause = IllegalStateException("boom")

        whenn()
        val error = domainErrorOf(HttpStatusCode.InternalServerError, serverError = null, cause = cause)

        then()
        assertTrue(error is Error.UnknownError)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `should not lose the failure when there is no cause to attach`() = runUnitTest {
        given()

        whenn()
        val error = domainErrorOf(HttpStatusCode.BadGateway, serverError = null, cause = null)

        then()
        assertTrue(error is Error.UnknownError)
    }
}

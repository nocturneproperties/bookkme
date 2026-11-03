package library.stepup.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.stepup.StepUpClaims
import library.stepup.StepUpGrant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class StepUpGrantTest {

    @Test
    fun `should bind a personal data export to the caller alone`() = runUnitTest {
        given()
        val userId = Uuid.random()

        whenn()
        val grant = StepUpGrant.personalDataExport(userId)

        then()
        assertEquals(StepUpGrant(userId, StepUpClaims.ACTION_EXPORT_PERSONAL_DATA, businessId = Uuid.NIL, targetId = userId), grant)
    }

    @Test
    fun `should bind a business data export to the caller and the business`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val businessId = Uuid.random()

        whenn()
        val grant = StepUpGrant.businessDataExport(userId, businessId)

        then()
        assertEquals(StepUpGrant(userId, StepUpClaims.ACTION_EXPORT_BUSINESS_DATA, businessId = businessId, targetId = businessId), grant)
    }

    @Test
    fun `should bind a client deletion to the caller, the business and the client`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val clientId = Uuid.random()

        whenn()
        val grant = StepUpGrant.clientDeletion(userId, businessId, clientId)

        then()
        assertEquals(StepUpGrant(userId, StepUpClaims.ACTION_DELETE_CLIENT, businessId = businessId, targetId = clientId), grant)
    }
}

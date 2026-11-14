package library.actionconfirmation.impl

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.actionconfirmation.ActionConfirmationClaims
import library.actionconfirmation.ActionConfirmationGrant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ActionConfirmationGrantTest {

    @Test
    fun `should bind an account deletion to the caller alone`() = runUnitTest {
        given()
        val userId = Uuid.random()

        whenn()
        val grant = ActionConfirmationGrant.accountDeletion(userId)

        then()
        assertEquals(
            ActionConfirmationGrant(
                userId,
                ActionConfirmationClaims.ACTION_DELETE_ACCOUNT,
                businessId = Uuid.NIL,
                targetId = userId,
            ),
            grant,
        )
    }

    @Test
    fun `should bind a personal data export to the caller alone`() = runUnitTest {
        given()
        val userId = Uuid.random()

        whenn()
        val grant = ActionConfirmationGrant.personalDataExport(userId)

        then()
        assertEquals(
            ActionConfirmationGrant(
                userId,
                ActionConfirmationClaims.ACTION_EXPORT_PERSONAL_DATA,
                businessId = Uuid.NIL,
                targetId = userId,
            ),
            grant,
        )
    }

    @Test
    fun `should bind a business data export to the caller and the business`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val businessId = Uuid.random()

        whenn()
        val grant = ActionConfirmationGrant.businessDataExport(userId, businessId)

        then()
        assertEquals(
            ActionConfirmationGrant(
                userId,
                ActionConfirmationClaims.ACTION_EXPORT_BUSINESS_DATA,
                businessId = businessId,
                targetId = businessId,
            ),
            grant,
        )
    }

    @Test
    fun `should bind a client deletion to the caller, the business and the client`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val clientId = Uuid.random()

        whenn()
        val grant = ActionConfirmationGrant.clientDeletion(userId, businessId, clientId)

        then()
        assertEquals(
            ActionConfirmationGrant(
                userId,
                ActionConfirmationClaims.ACTION_DELETE_CLIENT,
                businessId = businessId,
                targetId = clientId,
            ),
            grant,
        )
    }
}

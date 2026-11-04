package library.actionconfirmation

import kotlin.uuid.Uuid

data class ActionConfirmationGrant(
    val userId: Uuid,
    val action: String,
    val businessId: Uuid,
    val targetId: Uuid,
) {
    companion object {
        fun accountDeletion(userId: Uuid) = ActionConfirmationGrant(
            userId = userId,
            action = ActionConfirmationClaims.ACTION_DELETE_ACCOUNT,
            businessId = Uuid.NIL,
            targetId = userId,
        )

        fun personalDataExport(userId: Uuid) = ActionConfirmationGrant(
            userId = userId,
            action = ActionConfirmationClaims.ACTION_EXPORT_PERSONAL_DATA,
            businessId = Uuid.NIL,
            targetId = userId,
        )

        fun businessDataExport(userId: Uuid, businessId: Uuid) = ActionConfirmationGrant(
            userId = userId,
            action = ActionConfirmationClaims.ACTION_EXPORT_BUSINESS_DATA,
            businessId = businessId,
            targetId = businessId,
        )

        fun clientDeletion(userId: Uuid, businessId: Uuid, clientId: Uuid) = ActionConfirmationGrant(
            userId = userId,
            action = ActionConfirmationClaims.ACTION_DELETE_CLIENT,
            businessId = businessId,
            targetId = clientId,
        )
    }
}

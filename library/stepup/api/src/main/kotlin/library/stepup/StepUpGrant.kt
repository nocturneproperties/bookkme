package library.stepup

import kotlin.uuid.Uuid

data class StepUpGrant(
    val userId: Uuid,
    val action: String,
    val businessId: Uuid,
    val targetId: Uuid,
) {
    companion object {
        fun personalDataExport(userId: Uuid) = StepUpGrant(
            userId = userId,
            action = StepUpClaims.ACTION_EXPORT_PERSONAL_DATA,
            businessId = Uuid.NIL,
            targetId = userId,
        )

        fun businessDataExport(userId: Uuid, businessId: Uuid) = StepUpGrant(
            userId = userId,
            action = StepUpClaims.ACTION_EXPORT_BUSINESS_DATA,
            businessId = businessId,
            targetId = businessId,
        )

        fun clientDeletion(userId: Uuid, businessId: Uuid, clientId: Uuid) = StepUpGrant(
            userId = userId,
            action = StepUpClaims.ACTION_DELETE_CLIENT,
            businessId = businessId,
            targetId = clientId,
        )
    }
}

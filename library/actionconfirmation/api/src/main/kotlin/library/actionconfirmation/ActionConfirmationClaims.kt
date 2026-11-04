package library.actionconfirmation

object ActionConfirmationClaims {
    const val CLAIM_USER_ID = "user_id"
    const val CLAIM_ACTION = "action"
    const val CLAIM_BUSINESS_ID = "business_id"
    const val CLAIM_TARGET_ID = "target_id"

    const val ACTION_DELETE_CLIENT = "client.delete"
    const val ACTION_EXPORT_PERSONAL_DATA = "user.export"
    const val ACTION_EXPORT_BUSINESS_DATA = "business.export"
    const val ACTION_DELETE_ACCOUNT = "account.delete"
}

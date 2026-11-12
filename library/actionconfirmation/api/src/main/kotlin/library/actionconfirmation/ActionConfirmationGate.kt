package library.actionconfirmation

interface ActionConfirmationGate {
    suspend fun <T> spend(
        token: String,
        grant: ActionConfirmationGrant,
        rejection: () -> Throwable,
        action: suspend () -> T,
    ): T
}

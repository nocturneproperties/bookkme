package library.stepup

interface StepUpGate {
    suspend fun <T> spend(token: String, grant: StepUpGrant, rejection: () -> Throwable, action: suspend () -> T): T
}

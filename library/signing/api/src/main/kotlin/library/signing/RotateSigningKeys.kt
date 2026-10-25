package library.signing

import kotlin.time.Duration

interface RotateSigningKeys {
    suspend operator fun invoke(retireInterval: Duration): Result<Unit>
}

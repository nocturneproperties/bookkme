package library.export

import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.CoreErrorCodes
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

interface DataExports {
    suspend fun start(ownerId: Uuid): DataExport

    suspend fun status(ownerId: Uuid): DataExport

    suspend fun document(ownerId: Uuid): DataExportDocument?

    suspend fun discard(ownerId: Uuid)

    sealed interface Error {
        class TooFrequent :
            BusinessError(
                statusCode = HttpStatusCode.TooManyRequests.value,
                code = CoreErrorCodes.DATA_EXPORT_TOO_FREQUENT,
                message = "Only one data export can be started per day",
            ),
            Error
    }

    companion object {
        val MIN_INTERVAL = 1.days
        val DOCUMENT_LIFETIME = 72.hours
        val BUILD_LEASE = 10.minutes
        const val MAX_BUILD_ATTEMPTS = 3
    }
}

package library.idempotency

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime

class CleanUpWorker(
    private val scope: CoroutineScope,
    private val jitter: Duration,
    private val interval: Duration,
    private val idempotentResponseRepository: IdempotentResponseRepository,
    private val storedResponseTTL: Duration,
) {
    private val logger = KtorSimpleLogger("CleanUpWorker")

    fun start() {
        val job =
            scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                while (isActive) {
                    delay(sleepDuration().toMillis())
                    try {
                        idempotentResponseRepository.deleteExpiredResponses(
                            OffsetDateTime.now().minus(storedResponseTTL),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error("Cannot clean up expired responses", e)
                    }
                }
            }
        job.start()
    }

    private fun sleepDuration(): Duration {
        val jitter = (0L..jitter.toMillis()).random()
        return interval.plus(Duration.ofMillis(jitter))
    }
}
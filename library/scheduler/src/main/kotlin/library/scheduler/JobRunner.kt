package library.scheduler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.Logger

internal class JobRunner(
    private val jobs: List<ScheduledJob>,
    dispatcher: CoroutineDispatcher,
    private val logger: Logger
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("scheduler"))

    fun start() {
        jobs.forEach { job ->
            scope.launch(CoroutineName(job.name)) {
                delay(job.initialDelay)
                while (isActive) {
                    try {
                        logger.debug("Starting '${job.name}'")
                        job.action()
                        logger.debug("Scheduled job '${job.name}' completed")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error("Scheduled job '${job.name}' failed", e)
                    }
                    delay(job.interval)
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}

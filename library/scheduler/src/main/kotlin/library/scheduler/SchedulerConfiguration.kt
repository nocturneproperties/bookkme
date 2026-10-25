package library.scheduler

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration

class SchedulerConfiguration {
    internal val jobs = mutableListOf<ScheduledJob>()

    /**
     * Dispatcher used to run all scheduled jobs.
     */
    var dispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Registers a job named [name] that runs [action] repeatedly every [interval],
     * starting after [initialDelay]. Job names must be unique within a single plugin installation.
     */
    fun job(
        name: String,
        interval: Duration,
        initialDelay: Duration = Duration.ZERO,
        action: suspend () -> Unit
    ) {
        require(interval.isPositive()) { "Job '$name' interval must be positive" }
        require(jobs.none { it.name == name }) { "Job '$name' is already scheduled" }
        jobs += ScheduledJob(name, interval, initialDelay, action)
    }
}

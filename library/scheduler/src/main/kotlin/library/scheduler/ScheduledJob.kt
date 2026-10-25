package library.scheduler

import kotlin.time.Duration

internal data class ScheduledJob(
    val name: String,
    val interval: Duration,
    val initialDelay: Duration,
    val action: suspend () -> Unit
)

package library.scheduler.test

import library.scheduler.SchedulerConfiguration
import kotlin.time.Duration

fun SchedulerConfiguration.registeredJobs(): Map<String, Duration> = jobs.associate { it.name to it.interval }

suspend fun SchedulerConfiguration.runJob(name: String) {
    val job = requireNotNull(jobs.find { it.name == name }) { "Job '$name' is not registered" }
    job.action()
}

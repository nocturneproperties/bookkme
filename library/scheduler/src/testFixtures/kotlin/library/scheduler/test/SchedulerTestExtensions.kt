package library.scheduler.test

import library.scheduler.SchedulerConfiguration
import kotlin.time.Duration

fun SchedulerConfiguration.registeredJobs(): Map<String, Duration> {
    return jobs.associate { it.name to it.interval }
}

fun SchedulerConfiguration.jobsWithoutSuccessLogging(): Set<String> {
    return jobs.filterNot { it.logSuccessfulRuns }.map { it.name }.toSet()
}

suspend fun SchedulerConfiguration.runJob(name: String) {
    val job = requireNotNull(jobs.find { it.name == name }) { "Job '$name' is not registered" }
    job.action()
}

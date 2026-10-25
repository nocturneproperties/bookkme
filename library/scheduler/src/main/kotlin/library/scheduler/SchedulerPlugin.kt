package library.scheduler

import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.MonitoringEvent
import io.ktor.util.logging.KtorSimpleLogger

val Scheduler = createApplicationPlugin(name = "Scheduler", createConfiguration = ::SchedulerConfiguration) {
    val logger = KtorSimpleLogger("Scheduler")
    val runner = JobRunner(pluginConfig.jobs, pluginConfig.dispatcher, logger)

    on(MonitoringEvent(ApplicationStarted)) {
        runner.start()
    }

    on(MonitoringEvent(ApplicationStopping)) {
        runner.stop()
    }
}

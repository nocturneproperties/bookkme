package com.bookk.core.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply
import org.slf4j.Marker

class SuppressedMessageFilter : TurboFilter() {
    var logger: String = ""
    var message: String = ""

    override fun decide(
        marker: Marker?,
        logger: Logger?,
        level: Level?,
        format: String?,
        params: Array<out Any?>?,
        t: Throwable?,
    ): FilterReply {
        val isSuppressed = logger?.name == this.logger && format == message
        return if (isSuppressed) FilterReply.DENY else FilterReply.NEUTRAL
    }
}

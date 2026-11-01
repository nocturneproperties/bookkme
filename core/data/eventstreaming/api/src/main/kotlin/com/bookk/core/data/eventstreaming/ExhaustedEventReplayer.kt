package com.bookk.core.data.eventstreaming

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

interface ExhaustedEventReplayer {
    fun start(scope: CoroutineScope): Job
}

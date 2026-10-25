package com.bookk.core.data.eventstreaming

import io.ktor.server.application.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import org.koin.core.scope.Scope

interface EventHandler {
    fun start(scope: CoroutineScope)
}

fun Application.startEventHandling(vararg scopes: Scope) {
    scopes.flatMap { it.getAll<EventHandler>() }.forEach {
        it.start(this + Dispatchers.Default)
    }
}
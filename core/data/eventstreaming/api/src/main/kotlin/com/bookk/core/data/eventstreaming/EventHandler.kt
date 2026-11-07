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
    startEventHandling(this + Dispatchers.Default, scopes.toList())
}

fun startEventHandling(coroutineScope: CoroutineScope, serviceScopes: List<Scope>) {
    serviceScopes.flatMap { it.getAll<EventHandler>() }.forEach { it.start(coroutineScope) }
    serviceScopes.mapNotNull { it.getOrNull<ExhaustedEventReplayer>() }.forEach { it.start(coroutineScope) }
}

package com.bookk.server.auth.client.impl

import io.ktor.resources.Resource
import kotlin.uuid.Uuid

object AuthRouting {
    @Resource("api")
    class Api {
        @Resource("/internal")
        class Internal(val parent: Api = Api()) {
            @Resource("/export")
            class Export(val parent: Internal = Internal()) {
                @Resource("/auth/{userId}")
                class Account(val parent: Export = Export(), val userId: Uuid)
            }
        }
    }
}

package com.bookk.server.user.client.impl

import io.ktor.resources.Resource
import kotlin.uuid.Uuid

object UserRouting {
    @Resource("api")
    class Api {
        @Resource("/internal")
        class Internal(val parent: Api = Api()) {
            @Resource("/user")
            class User(val parent: Internal = Internal()) {
                @Resource("/{id}")
                class Id(val parent: User = User(), val id: Uuid)

                @Resource("/{id}")
                class Delete(val parent: User = User(), val id: Uuid)

                @Resource("/{id}")
                class Edit(val parent: User = User(), val id: Uuid)

                @Resource("/email")
                class Email(val parent: User = User())
            }
        }
    }
}
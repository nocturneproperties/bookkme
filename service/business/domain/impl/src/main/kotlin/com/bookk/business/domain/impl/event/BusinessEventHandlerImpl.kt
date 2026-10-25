package com.bookk.business.domain.impl.event

import com.bookk.business.domain.api.business.operation.DeleteBusiness
import com.bookk.business.domain.api.user.operation.AnonymizeUserProfile
import com.bookk.business.domain.api.user.operation.SyncUserProfile
import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.core.data.eventstreaming.StandardEventConsumer
import com.bookk.core.data.eventstreaming.registerResultReceiver
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.user.client.api.event.UserEvent
import kotlinx.coroutines.CoroutineScope

internal class BusinessEventHandlerImpl(
    private val consumer: StandardEventConsumer,
    private val deleteBusiness: DeleteBusiness,
    private val syncUserProfile: SyncUserProfile,
    private val anonymizeUserProfile: AnonymizeUserProfile
) : EventHandler {
    override fun start(scope: CoroutineScope) {
        consumer
            .registerResultReceiver(AuthEvent.UserDeleted.TOPIC) { event: AuthEvent.UserDeleted ->
                deleteBusiness(event.userId).getOrThrow()
                anonymizeUserProfile(event.userId)
            }
            .registerResultReceiver(UserEvent.Updated.TOPIC) { event: UserEvent.Updated ->
                syncUserProfile(
                    userId = event.userId,
                    name = event.name,
                    lastName = event.lastName,
                    email = event.email,
                    phone = event.phone,
                    updatedAt = event.updatedAt
                )
            }
            .start(scope)
    }
}
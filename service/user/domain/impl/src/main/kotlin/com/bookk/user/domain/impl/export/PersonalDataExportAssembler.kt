package com.bookk.user.domain.impl.export

import com.bookk.business.domain.api.export.ExportUserBusinessData
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Error
import com.bookk.notifications.client.api.export.ExportUserNotificationData
import com.bookk.server.appointments.client.api.export.ExportUserAppointments
import com.bookk.server.auth.client.export.ExportAccountData
import com.bookk.server.user.client.api.event.UserEvent
import com.bookk.user.domain.api.export.ProfileExport
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import com.bookk.user.domain.datasource.UserDataExportDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import library.export.DataExportAssembler
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class PersonalDataExportAssembler(
    private val userDataExportDataSource: UserDataExportDataSource,
    private val legalDocumentDataSource: LegalDocumentDataSource,
    private val transactionManager: TransactionManager,
    private val exportAccountData: ExportAccountData,
    private val exportUserBusinessData: ExportUserBusinessData,
    private val exportUserAppointments: ExportUserAppointments,
    private val exportUserNotificationData: ExportUserNotificationData,
    private val eventProducer: StandardEventProducer,
    private val clock: Clock,
) : DataExportAssembler {

    override suspend fun assemble(ownerId: Uuid): String {
        val ownData = transactionManager.transactionOrThrow {
            OwnData(
                profile = userDataExportDataSource.getProfile(ownerId) ?: throw Error.NotFound(),
                legalAcceptances = legalDocumentDataSource.getAcceptances(ownerId).map {
                    PersonalDataExport.LegalAcceptanceExport(
                        document = it.document.name,
                        version = it.version,
                        acceptedAt = it.acceptedAt,
                    )
                },
            )
        }
        val document = coroutineScope {
            val account = async { exportAccountData(ownerId).getOrThrow() }
            val businesses = async { exportUserBusinessData(ownerId).getOrThrow() }
            val appointments = async { exportUserAppointments(ownerId).getOrThrow() }
            val notifications = async { exportUserNotificationData(ownerId).getOrThrow() }
            PersonalDataExport(
                formatVersion = PersonalDataExport.FORMAT_VERSION,
                generatedAt = clock.now(),
                userId = ownerId,
                profile = ownData.profile,
                legalAcceptances = ownData.legalAcceptances,
                account = account.await(),
                businesses = businesses.await(),
                appointments = appointments.await(),
                notifications = notifications.await(),
            )
        }
        return json.encodeToString(document)
    }

    override suspend fun announceReady(ownerId: Uuid, expiresAt: Instant) {
        eventProducer.send(UserEvent.DataExportReady(userId = ownerId, expiresAt = expiresAt))
    }

    private class OwnData(
        val profile: ProfileExport,
        val legalAcceptances: List<PersonalDataExport.LegalAcceptanceExport>,
    )

    private companion object {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}

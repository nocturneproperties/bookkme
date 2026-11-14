package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.entity.AppointmentEntity
import com.bookk.appointments.data.orm.entity.AppointmentRequestEntity
import com.bookk.appointments.data.orm.entity.withDetails
import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentRequestTable
import com.bookk.appointments.data.orm.table.AppointmentTable
import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.datasource.UserDataExportDataSource
import com.bookk.core.data.DataSource
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.uuid.Uuid

internal class UserDataExportDataSourceImpl(
    private val snapshotCipher: SnapshotCipher,
) : DataSource(), UserDataExportDataSource {

    override suspend fun getAppointmentsOfUser(userId: Uuid): List<Appointment> = dbQuery {
        AppointmentEntity
            .find {
                (AppointmentTable.clientUserId eq userId) or (AppointmentTable.employeeUserId eq userId) or
                    (AppointmentTable.initiatorUserId eq userId)
            }
            .orderBy(AppointmentTable.dateStart to SortOrder.ASC)
            .toList()
            .withDetails()
            .let { appointments ->
                val reader = snapshotCipher.reader(appointments.map { it.storedSnapshot() }, AppointmentTable.tableName)
                appointments.map { it.domain(reader) }
            }
    }

    override suspend fun getRequestsOfUser(userId: Uuid): List<AppointmentRequest> = dbQuery {
        AppointmentRequestEntity
            .find {
                (AppointmentRequestTable.clientUserId eq userId) or
                    (AppointmentRequestTable.employeeUserId eq userId) or
                    (AppointmentRequestTable.initiatorUserId eq userId)
            }
            .orderBy(AppointmentRequestTable.dateStart to SortOrder.ASC)
            .with(AppointmentRequestEntity::services)
            .toList()
            .let { requests ->
                val reader = snapshotCipher.reader(
                    requests.map {
                        it.storedSnapshot()
                    },
                    AppointmentRequestTable.tableName,
                )
                requests.map { it.domain(reader) }
            }
    }

    override suspend fun getBusinessNames(businessIds: Collection<Uuid>): Map<Uuid, String> = dbQuery {
        AppointmentBusinessTable
            .select(AppointmentBusinessTable.id, AppointmentBusinessTable.name)
            .where { AppointmentBusinessTable.id inList businessIds.distinct() }
            .associate { it[AppointmentBusinessTable.id].value to it[AppointmentBusinessTable.name] }
    }
}

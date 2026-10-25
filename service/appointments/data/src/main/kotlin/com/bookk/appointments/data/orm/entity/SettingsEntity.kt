package com.bookk.appointments.data.orm.entity

import com.bookk.appointments.data.orm.table.SettingsTable
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentSettingsUpdate
import com.bookk.core.data.DecoratorUuidEntityClass
import kotlinx.datetime.TimeZone
import library.permissions.ResourcePermission
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class SettingsEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var business by AppointmentBusinessEntity referencedOn SettingsTable.businessId
    var automaticApproval by SettingsTable.automaticApproval
    var automaticCompletion by SettingsTable.automaticCompletion
    var inBetweenBreakInMinutes by SettingsTable.inBetweenBreakInMinutes
    var appointmentNote by SettingsTable.appointmentNote
    var updatedAt by SettingsTable.updatedAt

    fun domain(): AppointmentSettings = AppointmentSettings(
        id = id.value,
        businessId = business.id.value,
        timeZone = TimeZone.of(business.timezone),
        schedule = business.schedule(),
        automaticApproval = automaticApproval,
        inBetweenBreakInMinutes = inBetweenBreakInMinutes,
        appointmentNote = appointmentNote,
        permissions = ResourcePermission.NONE,
        automaticCompletion = automaticCompletion
    )

    companion object : DecoratorUuidEntityClass<SettingsEntity>(SettingsTable) {
        fun new(settings: AppointmentSettings): SettingsEntity = new {
            business = AppointmentBusinessEntity[settings.businessId]
            inBetweenBreakInMinutes = settings.inBetweenBreakInMinutes
            appointmentNote = settings.appointmentNote
            automaticApproval = settings.automaticApproval
            automaticCompletion = settings.automaticCompletion
        }

        fun findByBusinessIdAndUpdate(update: AppointmentSettingsUpdate): SettingsEntity? =
            findSingleByAndUpdate(op = SettingsTable.businessId eq update.businessId) {
                it.inBetweenBreakInMinutes = update.inBetweenBreakInMinutes
                it.appointmentNote = update.appointmentNote
                it.automaticApproval = update.automaticApproval
                update.automaticCompletion?.let { automaticCompletion -> it.automaticCompletion = automaticCompletion }
                it.updatedAt = Clock.System.now()
            }
    }
}

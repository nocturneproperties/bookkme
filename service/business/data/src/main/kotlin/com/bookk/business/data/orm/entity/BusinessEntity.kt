package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.BusinessDayOffTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.BusinessWorkingHoursTable
import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.business.entity.BusinessUpdateModel
import com.bookk.core.data.DecoratorUuidEntityClass
import kotlinx.datetime.TimeZone
import library.schedule.Schedule
import library.schedule.toWorkingDays
import library.schedule.toWorkingDaysMask
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class BusinessEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var userId by BusinessTable.userId
    var name by BusinessTable.name
    var description by BusinessTable.description
    var address by BusinessTable.address
    var latitude by BusinessTable.latitude
    var longitude by BusinessTable.longitude
    var timezone by BusinessTable.timezone
    var currency by BusinessTable.currency
    var instagram by BusinessTable.instagram
    var telegram by BusinessTable.telegram
    var viber by BusinessTable.viber
    var whatsapp by BusinessTable.whatsapp
    var phone by BusinessTable.phone
    var createdAt by BusinessTable.createdAt
    var updatedAt by BusinessTable.updatedAt
    var workingDays by BusinessTable.workingDays
    val workingHours by BusinessWorkingHourEntity referrersOn BusinessWorkingHoursTable.businessId
    val dayOffs by BusinessDayOffEntity referrersOn BusinessDayOffTable.businessId

    fun toDomain(): Business = Business(
        id = id.value,
        ownerId = userId,
        name = name,
        description = description,
        location = if (latitude != null && longitude != null) {
            Business.Location(latitude ?: 0.0, longitude ?: 0.0)
        } else null,
        currencyCode = currency,
        address = address,
        timeZone = TimeZone.of(timezone),
        schedule = Schedule(
            workingDays = workingDays.toWorkingDays(),
            workingHours = workingHours.toWorkingHours(),
            dayOffs = dayOffs.map { it.domain() }
        ),
        socials = listOf(
            Business.Social(Business.SocialKind.PHONE, phone.orEmpty()),
            Business.Social(Business.SocialKind.INSTAGRAM, instagram.orEmpty()),
            Business.Social(Business.SocialKind.TELEGRAM, telegram.orEmpty()),
            Business.Social(Business.SocialKind.WHATSAPP, whatsapp.orEmpty()),
            Business.Social(Business.SocialKind.VIBER, viber.orEmpty())
        ),
        permissions = BusinessPermissions.NONE,
        employeeId = null,
        clientId = null
    )

    private fun replaceSchedule(schedule: Schedule) {
        workingDays = schedule.activeDays().toWorkingDaysMask()
        BusinessWorkingHourEntity.batchReplace(id.value, schedule.workingHours())
        BusinessDayOffEntity.batchReplace(id.value, schedule.dayOffs)
    }

    private fun updateSocials(socials: List<Business.Social>) {
        for (social in socials) {
            when (social.kind) {
                Business.SocialKind.INSTAGRAM -> instagram = social.value
                Business.SocialKind.TELEGRAM -> telegram = social.value
                Business.SocialKind.VIBER -> viber = social.value
                Business.SocialKind.WHATSAPP -> whatsapp = social.value
                Business.SocialKind.PHONE -> phone = social.value
            }
        }
    }

    companion object : DecoratorUuidEntityClass<BusinessEntity>(BusinessTable) {

        fun new(userId: Uuid, name: String, currencyCode: String, timeZone: TimeZone): BusinessEntity = new {
            this.userId = userId
            this.name = name
            currency = currencyCode
            description = ""
            address = ""
            timezone = timeZone.id
        }.apply { replaceSchedule(Schedule()) }

        fun findByIdAndUpdate(model: BusinessUpdateModel, updatedAt: Instant) = findByIdAndUpdate(model.id) {
            model.name?.let { name -> it.name = name }
            model.description?.let { description -> it.description = description }
            model.address?.let { address -> it.address = address }
            model.location?.let { location ->
                it.latitude = location.lat
                it.longitude = location.lng
            }
            model.timeZone?.let { timezone -> it.timezone = timezone.id }
            model.currencyCode?.let { currencyCode -> it.currency = currencyCode }
            model.socials?.let { socials -> it.updateSocials(socials) }
            model.schedule?.let { schedule -> it.replaceSchedule(schedule) }
            it.updatedAt = updatedAt
        }
    }
}

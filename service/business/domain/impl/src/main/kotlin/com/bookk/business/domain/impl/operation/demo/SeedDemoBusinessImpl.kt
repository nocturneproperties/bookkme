package com.bookk.business.domain.impl.operation.demo

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.operation.SeedDemoBusiness
import com.bookk.business.domain.api.employee.entity.EmployeeUpdateModel
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.business.domain.datasource.UserLimitsDataSource
import com.bookk.business.domain.impl.operation.business.BusinessFounder
import com.bookk.business.domain.impl.operation.business.BusinessPlan
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.api.event.BusinessEvent
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.UserSnapshot
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.TimeZone
import library.email.PlaceholderEmail
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class SeedDemoBusinessImpl(
    private val userClient: UserClient,
    private val userLimitsDataSource: UserLimitsDataSource,
    private val businessDataSource: BusinessDataSource,
    private val businessFounder: BusinessFounder,
    private val serviceDataSource: ServiceDataSource,
    private val employeeDataSource: EmployeeDataSource,
    private val clientDataSource: ClientDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : SeedDemoBusiness {

    override suspend fun invoke(
        ownerUserId: Uuid,
        timeZoneId: String,
        language: Language,
    ): Result<Unit> = runSuspendCatching {
        val owner = userClient.getUserById(ownerUserId).getOrElse { failure ->
            if (failure.isUserGone()) return@runSuspendCatching
            throw failure
        }
        val plan = BusinessPlan(
            name = DemoBusinessSample.BUSINESS_NAME,
            currencyCode = DemoBusinessSample.currencyFor(language).unit.code,
            timeZone = timeZoneOrUtc(timeZoneId),
            demo = owner.isStillDemo(),
        )
        transactionManager.transactionOrThrow {
            userLimitsDataSource.lockForUser(ownerUserId)
            if (businessDataSource.countBusinessesOwnedBy(ownerUserId) > 0) return@transactionOrThrow
            seed(owner, plan, DemoBusinessSample.currencyFor(language))
        }
    }

    private suspend fun seed(owner: UserSnapshot, plan: BusinessPlan, currency: SampleCurrency) {
        val business = businessFounder.found(owner, plan)
        val employeeId = requireNotNull(business.employeeId)
        val services = createServices(business.id, currency)
        employeeDataSource.updateEmployee(ownerProvidingAll(owner, business, employeeId, services))
        val clients = clientDataSource.createDetachedClients(business.id, DemoBusinessSample.clients())
        eventProducer.send(
            BusinessEvent.DemoBusinessSeeded(
                businessId = business.id,
                ownerUserId = owner.id,
                employeeId = employeeId,
                serviceIds = services.map { it.id },
                clientIds = clients.map { it.id },
            ),
        )
    }

    private suspend fun createServices(businessId: Uuid, currency: SampleCurrency): List<Service> {
        val now = Clock.System.now()
        return DemoBusinessSample.groups.flatMap { sampleGroup ->
            val group = serviceDataSource.createServiceGroup(
                ServiceGroup(Uuid.random(), businessId, sampleGroup.name, now),
            )
            sampleGroup.services.map { sample ->
                serviceDataSource.createService(
                    Service(
                        id = Uuid.random(),
                        businessId = businessId,
                        group = group,
                        name = sample.name,
                        duration = sample.duration,
                        price = currency.priceOf(sample),
                        isAvailable = true,
                        createdAt = now,
                    ),
                )
            }
        }
    }

    private fun ownerProvidingAll(
        owner: UserSnapshot,
        business: Business,
        employeeId: Uuid,
        services: List<Service>,
    ): EmployeeUpdateModel {
        return EmployeeUpdateModel(
            id = employeeId,
            businessId = business.id,
            name = owner.name,
            lastName = owner.lastName,
            phone = owner.phone,
            email = owner.email,
            services = services,
            schedule = business.schedule.workingWeek(),
        )
    }

    private fun timeZoneOrUtc(id: String): TimeZone {
        return runCatching { TimeZone.of(id) }.getOrDefault(TimeZone.UTC)
    }

    private fun UserSnapshot.isStillDemo(): Boolean {
        return PlaceholderEmail.isPlaceholder(email)
    }

    private fun Throwable.isUserGone(): Boolean {
        return this is BusinessError && statusCode == HttpStatusCode.NotFound.value
    }
}

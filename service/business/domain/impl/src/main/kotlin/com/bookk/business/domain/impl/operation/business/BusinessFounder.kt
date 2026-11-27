package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessLinkDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.server.user.client.api.UserSnapshot
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class BusinessFounder(
    private val businessDataSource: BusinessDataSource,
    private val employeeDataSource: EmployeeDataSource,
    private val businessLinkDataSource: BusinessLinkDataSource,
) {
    suspend fun found(owner: UserSnapshot, plan: BusinessPlan): Business {
        val business = businessDataSource.createBusiness(
            userId = owner.id,
            name = plan.name,
            currencyCode = plan.currencyCode,
            timeZone = plan.timeZone,
            demo = plan.demo,
        )
        val ownerEmployee = employeeDataSource.createEmployee(ownerEmployeeOf(business, owner))
        if (plan.demo) assignDemoLink(business.id)
        return business.copy(permissions = BusinessPermissions.FULL, employeeId = ownerEmployee.id)
    }

    private fun ownerEmployeeOf(business: Business, owner: UserSnapshot): Employee {
        return Employee(
            id = Uuid.random(),
            businessId = business.id,
            name = owner.name,
            lastName = owner.lastName,
            phone = owner.phone,
            email = owner.email,
            userId = owner.id,
            services = emptyList(),
            schedule = business.schedule.workingWeek(),
            createdAt = Clock.System.now(),
            permissions = BusinessPermissions.FULL,
            suspendedAt = null,
        )
    }

    private suspend fun assignDemoLink(businessId: Uuid) {
        repeat(DEMO_LINK_ATTEMPTS) {
            val name = BusinessLinkPolicy.randomDemoName()
            if (businessLinkDataSource.getLinkByNameForUpdate(name) == null) {
                return businessLinkDataSource.createLink(businessId, name)
            }
        }
        throw IllegalStateException("No free demo link name after $DEMO_LINK_ATTEMPTS attempts")
    }

    companion object {
        private const val DEMO_LINK_ATTEMPTS = 5
    }
}

internal class BusinessPlan(
    val name: String,
    val currencyCode: String,
    val timeZone: TimeZone,
    val demo: Boolean,
)

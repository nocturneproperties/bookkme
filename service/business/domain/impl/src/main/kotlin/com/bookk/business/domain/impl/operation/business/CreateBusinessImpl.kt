package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessCreateRequest
import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.business.operation.CreateBusiness
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.user.client.UserClient
import library.schedule.Schedule
import library.validation.CurrencyValidator
import library.validation.NameValidator
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class CreateBusinessImpl(
    private val businessDataSource: BusinessDataSource,
    private val employeeDataSource: EmployeeDataSource,
    private val userClient: UserClient,
    private val transactionManager: TransactionManager
) : CreateBusiness {
    override suspend fun invoke(
        userId: Uuid,
        request: BusinessCreateRequest
    ): Result<Business> = transactionManager.transaction {
        if (!NameValidator.isValid(request.name)) {
            throw CreateBusiness.Error.BusinessValidationError()
        }
        if (!CurrencyValidator.isValid(request.currencyCode)) throw CreateBusiness.Error.InvalidCurrency()
        if (businessDataSource.isBusinessExist(userId)) throw CreateBusiness.Error.BusinessExist()
        val business = businessDataSource.createBusiness(userId, request.name, request.currencyCode, request.timeZone)
        val owner = userClient.getUserById(userId).getOrThrow()
        val ownerEmployee = employeeDataSource.createEmployee(
            Employee(
                id = Uuid.random(),
                businessId = business.id,
                name = owner.name,
                lastName = owner.lastName,
                phone = owner.phone,
                email = owner.email,
                userId = userId,
                services = emptyList(),
                schedule = Schedule.empty(),
                createdAt = Clock.System.now(),
                permissions = BusinessPermissions.FULL,
                suspendedAt = null
            )
        )
        business.copy(permissions = BusinessPermissions.FULL, employeeId = ownerEmployee.id)
    }
}

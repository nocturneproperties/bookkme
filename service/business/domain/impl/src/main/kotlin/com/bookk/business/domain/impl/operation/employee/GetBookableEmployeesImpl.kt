package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.employee.entity.BookableEmployee
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.operation.GetBookableEmployees
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetBookableEmployeesImpl(
    private val employeeDataSource: EmployeeDataSource,
    private val transactionManager: TransactionManager,
) : GetBookableEmployees {
    override suspend fun invoke(businessId: Uuid): Result<List<BookableEmployee>> = transactionManager.transaction {
        employeeDataSource.getActiveEmployees(businessId).map { it.bookable() }
    }

    private fun Employee.bookable(): BookableEmployee {
        return BookableEmployee(
            id = id,
            businessId = businessId,
            name = name,
            lastName = lastName,
            services = services,
            schedule = schedule,
        )
    }
}

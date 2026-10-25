package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeUpdateModel
import com.bookk.business.domain.api.employee.operation.GetEmployees
import com.bookk.business.domain.api.employee.operation.SetEmployeePermissions
import com.bookk.business.domain.api.employee.operation.SetEmployeeSuspension
import com.bookk.business.domain.api.employee.operation.UpdateEmployee
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.business.microservice.route.BusinessRouting
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.SimpleServerError
import com.bookk.core.service.test.createTestClient
import com.bookk.core.service.test.routeTest
import com.bookk.core.service.test.setupApplication
import com.bookk.core.test.given
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AppPrincipal
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class EmployeeCrudTest {

    private val businessId = Uuid.random()
    private val userId = Uuid.random()

    private fun createTestEmployee() = Employee.stub(businessId = businessId)

    private fun jwtAuthentication(): Application.() -> Unit = {
        install(Authentication) {
            provider {
                authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
            }
        }
    }

    private fun ApplicationTestBuilder.authenticatedApplication(useCase: UpdateEmployee) = setupApplication(
        extension = jwtAuthentication(),
        diModule = module { single { useCase } },
        routeUnderTest = { employeeCrud() }
    )

    private fun ApplicationTestBuilder.authenticatedApplication(useCase: SetEmployeePermissions) = setupApplication(
        extension = jwtAuthentication(),
        diModule = module { single { useCase } },
        routeUnderTest = { employeeCrud() }
    )

    private fun ApplicationTestBuilder.authenticatedApplication(useCase: GetEmployees) = setupApplication(
        extension = jwtAuthentication(),
        diModule = module { single { useCase } },
        routeUnderTest = { employeeCrud() }
    )

    private fun ApplicationTestBuilder.authenticatedApplication(useCase: SetEmployeeSuspension) = setupApplication(
        extension = jwtAuthentication(),
        diModule = module { single { useCase } },
        routeUnderTest = { employeeCrud() }
    )

    private fun suspensionResource(id: Uuid) = BusinessRouting.Api.Employee.Id.Suspension(
        BusinessRouting.Api.Employee.Id(BusinessRouting.Api.Employee(businessId = businessId), id)
    )

    private fun permissionsResource(id: Uuid) = BusinessRouting.Api.Employee.Id.Permissions(
        BusinessRouting.Api.Employee.Id(BusinessRouting.Api.Employee(businessId = businessId), id)
    )

    @Test
    fun `should update employee`() = routeTest {
        given()
        val useCase: UpdateEmployee = mockk()
        val employee = createTestEmployee()
        coEvery { useCase.invoke(userId, updateModel(employee)) } returns Result.success(employee)
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Employee.Id(BusinessRouting.Api.Employee(businessId = businessId), employee.id)) {
            setBody(updateModel(employee))
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return bad request when path business id does not match body business id`() = routeTest {
        given()
        val useCase: UpdateEmployee = mockk()
        val employee = createTestEmployee()
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Employee.Id(BusinessRouting.Api.Employee(businessId = Uuid.random()), employee.id)) {
            setBody(updateModel(employee))
        }

        then()
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `should return bad request when path id does not match body id`() = routeTest {
        given()
        val useCase: UpdateEmployee = mockk()
        val employee = createTestEmployee()
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Employee.Id(BusinessRouting.Api.Employee(businessId = businessId), Uuid.random())) {
            setBody(updateModel(employee))
        }

        then()
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `should return unprocessable entity when employee validation fails`() = routeTest {
        given()
        val useCase: UpdateEmployee = mockk()
        val employee = createTestEmployee()
        coEvery { useCase.invoke(userId, updateModel(employee)) } returns Result.failure(UpdateEmployee.Error.ValidationError())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Employee.Id(BusinessRouting.Api.Employee(businessId = businessId), employee.id)) {
            setBody(updateModel(employee))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_VALIDATION_ERROR, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when active day has no work hours`() = routeTest {
        given()
        val useCase: UpdateEmployee = mockk()
        val employee = createTestEmployee()
        coEvery { useCase.invoke(userId, updateModel(employee)) } returns Result.failure(UpdateEmployee.Error.ActiveDayWithoutWorkHours())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Employee.Id(BusinessRouting.Api.Employee(businessId = businessId), employee.id)) {
            setBody(updateModel(employee))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_ACTIVE_DAY_WITHOUT_WORK_HOURS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when day off range is invalid`() = routeTest {
        given()
        val useCase: UpdateEmployee = mockk()
        val employee = createTestEmployee()
        coEvery { useCase.invoke(userId, updateModel(employee)) } returns Result.failure(UpdateEmployee.Error.InvalidDayOffRange())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Employee.Id(BusinessRouting.Api.Employee(businessId = businessId), employee.id)) {
            setBody(updateModel(employee))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_INVALID_DAY_OFF_RANGE, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when caller has no permission to edit the employee`() = routeTest {
        given()
        val useCase: UpdateEmployee = mockk()
        val employee = createTestEmployee()
        coEvery { useCase.invoke(userId, updateModel(employee)) } returns Result.failure(Error.OperationNotAllowed())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Employee.Id(BusinessRouting.Api.Employee(businessId = businessId), employee.id)) {
            setBody(updateModel(employee))
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unauthorized when updating employee without authentication`() = routeTest {
        given()
        val useCase: UpdateEmployee = mockk()
        val employee = createTestEmployee()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { employeeCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Employee.Id(BusinessRouting.Api.Employee(businessId = businessId), employee.id)) {
            setBody(updateModel(employee))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return not found when employee does not belong to the business`() = routeTest {
        given()
        val useCase: UpdateEmployee = mockk()
        val employee = createTestEmployee()
        coEvery { useCase.invoke(userId, updateModel(employee)) } returns Result.failure(Error.NotFound())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Employee.Id(BusinessRouting.Api.Employee(businessId = businessId), employee.id)) {
            setBody(updateModel(employee))
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should accept a full employee body from clients that predate the update model`() = routeTest {
        given()
        val useCase: UpdateEmployee = mockk()
        val employee = createTestEmployee().copy(permissions = BusinessPermissions.FULL)
        coEvery { useCase.invoke(userId, updateModel(employee)) } returns Result.success(employee)
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Employee.Id(BusinessRouting.Api.Employee(businessId = businessId), employee.id)) {
            setBody(employee)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(employee, response.body<Employee>())
    }

    @Test
    fun `should set permissions for several resources in one request`() = routeTest {
        given()
        val useCase: SetEmployeePermissions = mockk()
        val id = Uuid.random()
        val clients = ResourcePermission(view = true, update = true, delete = false)
        val grants = mapOf(
            BusinessResource.CLIENTS to clients,
            BusinessResource.SERVICES to ResourcePermission.FULL
        )
        val updated = Employee.stub(
            id = id,
            businessId = businessId,
            permissions = BusinessPermissions.stub(clients = ResourcePermission(view = true, update = true, delete = false), services = ResourcePermission.FULL)
        )
        coEvery { useCase.invoke(userId, businessId, id, grants) } returns Result.success(updated)
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(permissionsResource(id)) {
            setBody(permissionsRequest(clients = clients, services = ResourcePermission.FULL))
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(updated, response.body<Employee>())
    }

    @Test
    fun `should map every named request field to its own business resource`() = routeTest {
        given()
        val useCase: SetEmployeePermissions = mockk()
        val id = Uuid.random()
        val business = ResourcePermission(view = true, update = false, delete = false)
        val employees = ResourcePermission(view = false, update = true, delete = false)
        val clients = ResourcePermission(view = false, update = false, delete = true)
        val services = ResourcePermission(view = true, update = true, delete = false)
        val appointments = ResourcePermission(view = false, update = true, delete = true)
        val grants = mapOf(
            BusinessResource.BUSINESS to business,
            BusinessResource.EMPLOYEES to employees,
            BusinessResource.CLIENTS to clients,
            BusinessResource.SERVICES to services,
            BusinessResource.APPOINTMENTS to appointments
        )
        coEvery { useCase.invoke(userId, businessId, id, grants) } returns Result.success(Employee.stub(id = id, businessId = businessId))
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(permissionsResource(id)) {
            setBody(permissionsRequest(business, employees, clients, services, appointments))
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should pass no grants when every request field is omitted`() = routeTest {
        given()
        val useCase: SetEmployeePermissions = mockk()
        val id = Uuid.random()
        val employee = Employee.stub(id = id, businessId = businessId)
        coEvery { useCase.invoke(userId, businessId, id, emptyMap()) } returns Result.success(employee)
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(permissionsResource(id)) {
            setBody(permissionsRequest())
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(employee, response.body<Employee>())
    }

    @Test
    fun `should return unprocessable entity when setting permissions for the business owner`() = routeTest {
        given()
        val useCase: SetEmployeePermissions = mockk()
        val id = Uuid.random()
        val grants = mapOf(BusinessResource.CLIENTS to ResourcePermission.NONE)
        coEvery { useCase.invoke(userId, businessId, id, grants) } returns
            Result.failure(SetEmployeePermissions.Error.OwnerPermissionsImmutable())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(permissionsResource(id)) {
            setBody(permissionsRequest(clients = grants.getValue(BusinessResource.CLIENTS)))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_OWNER_PERMISSIONS_IMMUTABLE, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when setting permissions for an unknown employee`() = routeTest {
        given()
        val useCase: SetEmployeePermissions = mockk()
        val id = Uuid.random()
        val grants = mapOf(BusinessResource.CLIENTS to ResourcePermission(view = true, update = false, delete = false))
        coEvery { useCase.invoke(userId, businessId, id, grants) } returns Result.failure(Error.NotFound())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(permissionsResource(id)) {
            setBody(permissionsRequest(clients = grants.getValue(BusinessResource.CLIENTS)))
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return not found when caller is not the business owner`() = routeTest {
        given()
        val useCase: SetEmployeePermissions = mockk()
        val id = Uuid.random()
        val grants = mapOf(BusinessResource.CLIENTS to ResourcePermission(view = true, update = false, delete = false))
        coEvery { useCase.invoke(userId, businessId, id, grants) } returns Result.failure(Error.OperationNotAllowed())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(permissionsResource(id)) {
            setBody(permissionsRequest(clients = grants.getValue(BusinessResource.CLIENTS)))
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unauthorized when setting permissions without authentication`() = routeTest {
        given()
        val useCase: SetEmployeePermissions = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { employeeCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(permissionsResource(Uuid.random())) {
            setBody(permissionsRequest(clients = ResourcePermission(view = true, update = false, delete = false)))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should suspend employee`() = routeTest {
        given()
        val useCase: SetEmployeeSuspension = mockk()
        val employee = createTestEmployee().copy(suspendedAt = Instant.fromEpochMilliseconds(1))
        coEvery { useCase.invoke(userId, businessId, employee.id, true) } returns Result.success(employee)
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(suspensionResource(employee.id)) { setBody(EmployeeSuspensionRequest(suspended = true)) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(employee, response.body<Employee>())
    }

    @Test
    fun `should reinstate employee`() = routeTest {
        given()
        val useCase: SetEmployeeSuspension = mockk()
        val employee = createTestEmployee()
        coEvery { useCase.invoke(userId, businessId, employee.id, false) } returns Result.success(employee)
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(suspensionResource(employee.id)) { setBody(EmployeeSuspensionRequest(suspended = false)) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(employee, response.body<Employee>())
    }

    @Test
    fun `should return unprocessable entity when suspending the business owner`() = routeTest {
        given()
        val useCase: SetEmployeeSuspension = mockk()
        val id = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, id, true) } returns
            Result.failure(SetEmployeeSuspension.Error.OwnerSuspensionNotAllowed())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(suspensionResource(id)) { setBody(EmployeeSuspensionRequest(suspended = true)) }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_OWNER_SUSPENSION_NOT_ALLOWED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when suspending an unknown employee`() = routeTest {
        given()
        val useCase: SetEmployeeSuspension = mockk()
        val id = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, id, true) } returns Result.failure(Error.NotFound())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(suspensionResource(id)) { setBody(EmployeeSuspensionRequest(suspended = true)) }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return not found when a non owner suspends an employee`() = routeTest {
        given()
        val useCase: SetEmployeeSuspension = mockk()
        val id = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, id, true) } returns Result.failure(Error.OperationNotAllowed())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.put(suspensionResource(id)) { setBody(EmployeeSuspensionRequest(suspended = true)) }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unauthorized when suspending without authentication`() = routeTest {
        given()
        val useCase: SetEmployeeSuspension = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { employeeCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(suspensionResource(Uuid.random())) { setBody(EmployeeSuspensionRequest(suspended = true)) }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return employees of the business`() = routeTest {
        given()
        val useCase: GetEmployees = mockk()
        val employees = listOf(
            createTestEmployee().copy(permissions = BusinessPermissions.FULL),
            createTestEmployee().copy(permissions = BusinessPermissions.VIEW_ONLY)
        )
        coEvery { useCase.invoke(userId, businessId) } returns Result.success(employees)
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Employee(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(employees, response.body<List<Employee>>())
    }

    @Test
    fun `should return not found when caller has no rights to list employees`() = routeTest {
        given()
        val useCase: GetEmployees = mockk()
        coEvery { useCase.invoke(userId, businessId) } returns Result.failure(Error.OperationNotAllowed())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Employee(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return forbidden with a distinct code when the caller is suspended`() = routeTest {
        given()
        val useCase: GetEmployees = mockk()
        coEvery { useCase.invoke(userId, businessId) } returns Result.failure(EmployeeAccessSuspended())
        authenticatedApplication(useCase)

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Employee(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_ACCESS_SUSPENDED, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when listing employees without authentication`() = routeTest {
        given()
        val useCase: GetEmployees = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { employeeCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Employee(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private fun updateModel(employee: Employee) = EmployeeUpdateModel(
        id = employee.id,
        businessId = employee.businessId,
        name = employee.name,
        lastName = employee.lastName,
        phone = employee.phone,
        email = employee.email,
        services = employee.services,
        schedule = employee.schedule
    )

    private fun permissionsRequest(
        business: ResourcePermission? = null,
        employees: ResourcePermission? = null,
        clients: ResourcePermission? = null,
        services: ResourcePermission? = null,
        appointments: ResourcePermission? = null
    ) = EmployeePermissionsRequest(
        business = business,
        employees = employees,
        clients = clients,
        services = services,
        appointments = appointments
    )
}

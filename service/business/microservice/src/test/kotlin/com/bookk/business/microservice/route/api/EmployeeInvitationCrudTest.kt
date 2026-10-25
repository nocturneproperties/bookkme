package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import com.bookk.business.domain.api.employee.operation.CreateEmployeeInvitation
import com.bookk.business.domain.api.employee.operation.GetEmployeeInvitations
import com.bookk.business.domain.api.employee.operation.JoinBusiness
import com.bookk.business.domain.api.employee.operation.RevokeEmployeeInvitation
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
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

internal class EmployeeInvitationCrudTest {

    private val businessId = Uuid.random()
    private val userId = Uuid.random()

    private fun revokeResource(id: Uuid) = BusinessRouting.Api.EmployeeInvitation.Revoke(
        BusinessRouting.Api.EmployeeInvitation(businessId = businessId),
        id
    )

    private fun jwtAuthentication(): Application.() -> Unit = {
        install(Authentication) {
            provider {
                authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
            }
        }
    }

    @Test
    fun `should create employee invitation`() = routeTest {
        given()
        val useCase: CreateEmployeeInvitation = mockk()
        val invitation = EmployeeInvitation.stub(businessId = businessId)
        coEvery { useCase.invoke(userId, businessId) } returns Result.success(invitation)

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.EmployeeInvitation(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(invitation.id, response.body<EmployeeInvitation>().id)
        assertEquals(invitation.code, response.body<EmployeeInvitation>().code)
    }

    @Test
    fun `should return not found when caller has no rights to invite`() = routeTest {
        given()
        val useCase: CreateEmployeeInvitation = mockk()
        coEvery { useCase.invoke(userId, businessId) } returns Result.failure(Error.OperationNotAllowed())

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.EmployeeInvitation(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unprocessable entity when the pending invitations limit is reached`() = routeTest {
        given()
        val useCase: CreateEmployeeInvitation = mockk()
        coEvery { useCase.invoke(userId, businessId) } returns
            Result.failure(CreateEmployeeInvitation.Error.PendingInvitationsLimitReached())

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.EmployeeInvitation(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            BusinessErrorCodes.BUSINESS_EMPLOYEE_PENDING_INVITATIONS_LIMIT_REACHED,
            response.body<SimpleServerError>().errorCode
        )
    }

    @Test
    fun `should return unprocessable entity when the daily invitations limit is reached`() = routeTest {
        given()
        val useCase: CreateEmployeeInvitation = mockk()
        coEvery { useCase.invoke(userId, businessId) } returns
            Result.failure(CreateEmployeeInvitation.Error.DailyInvitationsLimitReached())

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.EmployeeInvitation(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            BusinessErrorCodes.BUSINESS_EMPLOYEE_DAILY_INVITATIONS_LIMIT_REACHED,
            response.body<SimpleServerError>().errorCode
        )
    }

    @Test
    fun `should return unauthorized when creating invitation without authentication`() = routeTest {
        given()
        val useCase: CreateEmployeeInvitation = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.EmployeeInvitation(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return all invitations sent by the caller`() = routeTest {
        given()
        val useCase: GetEmployeeInvitations = mockk()
        val invitations = listOf(
            EmployeeInvitation.stub(businessId = businessId, invitedBy = userId),
            EmployeeInvitation.stub(businessId = businessId, invitedBy = userId)
        )
        coEvery { useCase.invoke(userId, businessId) } returns Result.success(invitations)

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.EmployeeInvitation(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(invitations.map { it.id }, response.body<List<EmployeeInvitation>>().map { it.id })
    }

    @Test
    fun `should return unauthorized when listing invitations without authentication`() = routeTest {
        given()
        val useCase: GetEmployeeInvitations = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.EmployeeInvitation(businessId = businessId))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should redeem employee invitation with a valid code`() = routeTest {
        given()
        val useCase: JoinBusiness = mockk()
        val employee = Employee.stub(businessId = businessId, userId = userId)
        coEvery { useCase.invoke(userId, "ABCD1234") } returns Result.success(employee)

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.RedeemEmployeeInvitation()) {
            setBody(EmployeeInvitationRedeemRequest(code = "ABCD1234"))
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(employee.id, response.body<Employee>().id)
    }

    @Test
    fun `should return not found when redeeming an unknown code`() = routeTest {
        given()
        val useCase: JoinBusiness = mockk()
        coEvery { useCase.invoke(userId, "ABCD1234") } returns Result.failure(Error.NotFound())

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.RedeemEmployeeInvitation()) {
            setBody(EmployeeInvitationRedeemRequest(code = "ABCD1234"))
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unprocessable entity when redeeming an already processed invitation`() = routeTest {
        given()
        val useCase: JoinBusiness = mockk()
        coEvery { useCase.invoke(userId, "ABCD1234") } returns
            Result.failure(JoinBusiness.Error.InvitationAlreadyProcessed())

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.RedeemEmployeeInvitation()) {
            setBody(EmployeeInvitationRedeemRequest(code = "ABCD1234"))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            BusinessErrorCodes.BUSINESS_EMPLOYEE_INVITATION_ALREADY_PROCESSED,
            response.body<SimpleServerError>().errorCode
        )
    }

    @Test
    fun `should return unprocessable entity when redeeming an empty code`() = routeTest {
        given()
        val useCase: JoinBusiness = mockk()
        coEvery { useCase.invoke(userId, "") } returns
            Result.failure(JoinBusiness.Error.EmptyInvitationCode())

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.RedeemEmployeeInvitation()) {
            setBody(EmployeeInvitationRedeemRequest(code = ""))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            BusinessErrorCodes.BUSINESS_EMPLOYEE_INVITATION_CODE_EMPTY,
            response.body<SimpleServerError>().errorCode
        )
    }

    @Test
    fun `should return unprocessable entity when redeeming user is already an employee`() = routeTest {
        given()
        val useCase: JoinBusiness = mockk()
        coEvery { useCase.invoke(userId, "ABCD1234") } returns
            Result.failure(JoinBusiness.Error.EmployeeExist())

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.RedeemEmployeeInvitation()) {
            setBody(EmployeeInvitationRedeemRequest(code = "ABCD1234"))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_EMPLOYEE_EXISTS, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when redeeming invitation without authentication`() = routeTest {
        given()
        val useCase: JoinBusiness = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.RedeemEmployeeInvitation()) {
            setBody(EmployeeInvitationRedeemRequest(code = "ABCD1234"))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should revoke employee invitation`() = routeTest {
        given()
        val useCase: RevokeEmployeeInvitation = mockk()
        val id = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, id) } returns Result.success(Unit)

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(revokeResource(id))

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should return not found when revoking unknown invitation`() = routeTest {
        given()
        val useCase: RevokeEmployeeInvitation = mockk()
        val id = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, id) } returns Result.failure(Error.NotFound())

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(revokeResource(id))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return not found when caller has no rights to revoke invitations`() = routeTest {
        given()
        val useCase: RevokeEmployeeInvitation = mockk()
        val id = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, id) } returns Result.failure(Error.OperationNotAllowed())

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(revokeResource(id))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unprocessable entity when revoking an already processed invitation`() = routeTest {
        given()
        val useCase: RevokeEmployeeInvitation = mockk()
        val id = Uuid.random()
        coEvery { useCase.invoke(userId, businessId, id) } returns
            Result.failure(RevokeEmployeeInvitation.Error.InvitationAlreadyProcessed())

        setupApplication(
            extension = jwtAuthentication(),
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(revokeResource(id))

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            BusinessErrorCodes.BUSINESS_EMPLOYEE_INVITATION_ALREADY_PROCESSED,
            response.body<SimpleServerError>().errorCode
        )
    }

    @Test
    fun `should return unauthorized when revoking invitation without authentication`() = routeTest {
        given()
        val useCase: RevokeEmployeeInvitation = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { employeeInvitationCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(revokeResource(Uuid.random()))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}

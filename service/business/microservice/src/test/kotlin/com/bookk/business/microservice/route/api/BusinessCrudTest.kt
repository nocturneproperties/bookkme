package com.bookk.business.microservice.route.api

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessCreateRequest
import com.bookk.business.domain.api.business.entity.BusinessUpdateModel
import com.bookk.business.domain.api.business.entity.UserBusinesses
import com.bookk.business.domain.api.business.operation.CreateBusiness
import com.bookk.business.domain.api.business.operation.GetBusinessById
import com.bookk.business.domain.api.business.operation.GetUserBusinesses
import com.bookk.business.domain.api.business.operation.SetDashboardBusiness
import com.bookk.business.domain.api.business.operation.UpdateBusiness
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
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import library.schedule.DayOfWeekSchedule
import library.schedule.DayOffRange
import library.schedule.Schedule
import library.schedule.WorkHour
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.uuid.Uuid

@Serializable
private data class LenientSchedule(
    val days: Map<DayOfWeek, DayOfWeekSchedule>,
    val dayOffs: List<DayOffRange> = emptyList()
)

@Serializable
private data class LenientUpdateModel(
    val id: Uuid,
    val name: String?,
    val description: String?,
    val address: String?,
    val location: Business.Location?,
    val currencyCode: String?,
    val timeZone: TimeZone?,
    val socials: List<Business.Social>?,
    val schedule: LenientSchedule?
)

internal class BusinessCrudTest {

    private val userId = Uuid.random()
    private val businessId = Uuid.random()

    private fun updateModel(
        id: Uuid = businessId,
        name: String? = null,
        schedule: Schedule? = null,
        dayOffs: List<DayOffRange> = emptyList()
    ) = BusinessUpdateModel(
        id = id,
        name = name,
        description = null,
        address = null,
        location = null,
        currencyCode = null,
        timeZone = null,
        socials = emptyList(),
        schedule = schedule?.copy(dayOffs = dayOffs)
    )

    private fun lenientUpdateModel(schedule: LenientSchedule) = LenientUpdateModel(
        id = businessId,
        name = null,
        description = null,
        address = null,
        location = null,
        currencyCode = null,
        timeZone = null,
        socials = emptyList(),
        schedule = schedule
    )

    private fun createTestBusiness(id: Uuid = businessId) = Business.stub(
        id = id,
        name = "Test Business",
        description = "Test Description",
        address = "Test Address"
    )

    @Test
    fun `should create business`() = routeTest {
        given()
        val useCase: CreateBusiness = mockk()
        val business = createTestBusiness()
        val name = "Test Business"
        val currencyCode = "USD"
        val timeZone = TimeZone.UTC
        val request = BusinessCreateRequest(name, currencyCode, timeZone)

        coEvery { useCase.invoke(userId, request) } returns Result.success(business)
        
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { businessCrud() }
        )
        
        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Business()) {
            setBody(request)
        }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should update business`() = routeTest {
        given()
        val useCase: UpdateBusiness = mockk()
        val updateModel = updateModel(name = "New Name")

        coEvery { useCase.invoke(userId, updateModel) } returns Result.success(Unit)
        
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { businessCrud() }
        )
        
        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id(id = businessId)) {
            setBody(updateModel)
        }
        
        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should return user businesses`() = routeTest {
        given()
        val useCase: GetUserBusinesses = mockk()
        val userBusinesses = UserBusinesses(businessId, listOf(createTestBusiness()))
        
        coEvery { useCase.invoke(userId) } returns Result.success(userBusinesses)
        
        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { businessCrud() }
        )
        
        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Business.Mine())
        
        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return business by id`() = routeTest {
        given()
        val useCase: GetBusinessById = mockk()
        val business = createTestBusiness()

        coEvery { useCase.invoke(businessId, userId) } returns Result.success(business)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Business.Id(id = businessId))

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return the caller's employee id and client id with the business`() = routeTest {
        given()
        val useCase: GetBusinessById = mockk()
        val employeeId = Uuid.random()
        val clientId = Uuid.random()
        coEvery { useCase.invoke(businessId, userId) } returns
            Result.success(Business.stub(id = businessId, employeeId = employeeId, clientId = clientId))

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val response = createTestClient().get(BusinessRouting.Api.Business.Id(id = businessId))

        then()
        val business = response.body<Business>()
        assertEquals(employeeId, business.employeeId)
        assertEquals(clientId, business.clientId)
    }

    @Test
    fun `should return empty employee id and client id when the caller is neither`() = routeTest {
        given()
        val useCase: GetBusinessById = mockk()
        coEvery { useCase.invoke(businessId, userId) } returns Result.success(Business.stub(id = businessId))

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module {
                single { useCase }
            },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val response = createTestClient().get(BusinessRouting.Api.Business.Id(id = businessId))

        then()
        val business = response.body<Business>()
        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(business.employeeId)
        assertNull(business.clientId)
    }

    @Test
    fun `should return unprocessable entity when business already exists`() = routeTest {
        given()
        val useCase: CreateBusiness = mockk()
        coEvery { useCase.invoke(userId, any()) } returns Result.failure(CreateBusiness.Error.BusinessExist())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Business()) {
            setBody(BusinessCreateRequest("Name", "USD", TimeZone.UTC))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_ALREADY_EXIST, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when business validation error`() = routeTest {
        given()
        val useCase: CreateBusiness = mockk()
        coEvery { useCase.invoke(userId, any()) } returns Result.failure(CreateBusiness.Error.BusinessValidationError())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Business()) {
            setBody(BusinessCreateRequest("Name", "USD", TimeZone.UTC))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_NAME_VALIDATION_ERROR, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return not found when getting business by id that does not exist`() = routeTest {
        given()
        val useCase: GetBusinessById = mockk()
        coEvery { useCase.invoke(businessId, userId) } returns Result.failure(GetBusinessById.Error.NotFound())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Business.Id(id = businessId))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_NOT_FOUND, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unauthorized when creating business without authentication`() = routeTest {
        given()
        val useCase: CreateBusiness = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Business()) {
            setBody(BusinessCreateRequest("Name", "USD", TimeZone.UTC))
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unauthorized when getting businesses without authentication`() = routeTest {
        given()
        val useCase: GetUserBusinesses = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Business.Mine())

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unauthorized when getting business by id without authentication`() = routeTest {
        given()
        val useCase: GetBusinessById = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.get(BusinessRouting.Api.Business.Id(id = businessId))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should accept a schedule that covers every day sent over the wire`() = routeTest {
        given()
        val useCase: UpdateBusiness = mockk()
        val completeSchedule = LenientSchedule(
            days = DayOfWeek.entries.associateWith {
                DayOfWeekSchedule(listOf(WorkHour(LocalTime(9, 0), LocalTime(17, 0))), isActive = true)
            }
        )
        val body = lenientUpdateModel(completeSchedule)
        coEvery { useCase.invoke(any(), any()) } returns Result.success(Unit)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id(id = businessId)) {
            setBody(ProtoBuf { encodeDefaults = true }.encodeToByteArray(body))
        }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
        coVerify(exactly = 1) { useCase.invoke(any(), any()) }
    }

    @Test
    fun `should return bad request when schedule does not cover every day`() = routeTest {
        given()
        val useCase: UpdateBusiness = mockk()
        val incompleteSchedule = LenientSchedule(
            days = mapOf(
                DayOfWeek.MONDAY to DayOfWeekSchedule(
                    workingTime = listOf(WorkHour(LocalTime(9, 0), LocalTime(17, 0))),
                    isActive = true
                )
            )
        )
        val body = lenientUpdateModel(incompleteSchedule)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id(id = businessId)) {
            setBody(ProtoBuf { encodeDefaults = true }.encodeToByteArray(body))
        }

        then()
        assertEquals(HttpStatusCode.BadRequest, response.status)
        coVerify(exactly = 0) { useCase.invoke(any(), any()) }
    }

    @Test
    fun `should update business schedule`() = routeTest {
        given()
        val useCase: UpdateBusiness = mockk()
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.SATURDAY),
            workingHours = mapOf(
                DayOfWeek.SATURDAY to listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0)))
            )
        )
        val updateModel = updateModel(
            schedule = schedule,
            dayOffs = listOf(DayOffRange(LocalDate(2099, 12, 30), LocalDate(2099, 12, 31)))
        )
        coEvery { useCase.invoke(userId, updateModel) } returns Result.success(Unit)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id(id = businessId)) {
            setBody(updateModel)
        }

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should return unprocessable entity when active day has no work hours`() = routeTest {
        given()
        val useCase: UpdateBusiness = mockk()
        coEvery { useCase.invoke(userId, any()) } returns
            Result.failure(UpdateBusiness.Error.ActiveDayWithoutWorkHours())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id(id = businessId)) {
            setBody(updateModel(schedule = Schedule(listOf(DayOfWeek.MONDAY), emptyMap())))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            BusinessErrorCodes.BUSINESS_ACTIVE_DAY_WITHOUT_WORK_HOURS,
            response.body<SimpleServerError>().errorCode
        )
    }

    @Test
    fun `should return unprocessable entity when day off range start date is not before end date`() = routeTest {
        given()
        val useCase: UpdateBusiness = mockk()
        coEvery { useCase.invoke(userId, any()) } returns Result.failure(UpdateBusiness.Error.InvalidDayOffRange())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id(id = businessId)) {
            setBody(updateModel(dayOffs = listOf(DayOffRange(LocalDate(2099, 12, 31), LocalDate(2099, 12, 30)))))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(
            BusinessErrorCodes.BUSINESS_INVALID_DAY_OFF_RANGE,
            response.body<SimpleServerError>().errorCode
        )
    }

    @Test
    fun `should return not found when user is not allowed to update the business`() = routeTest {
        given()
        val useCase: UpdateBusiness = mockk()
        coEvery { useCase.invoke(userId, any()) } returns Result.failure(Error.OperationNotAllowed())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id(id = businessId)) {
            setBody(updateModel(name = "New Name"))
        }

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return bad request when path id does not match body id`() = routeTest {
        given()
        val useCase: UpdateBusiness = mockk()

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id(id = Uuid.random())) {
            setBody(updateModel(id = businessId, name = "New Name"))
        }

        then()
        assertEquals(HttpStatusCode.BadRequest, response.status)
        coVerify(exactly = 0) { useCase.invoke(any(), any()) }
    }

    @Test
    fun `should return unauthorized when updating business without authentication`() = routeTest {
        given()
        val useCase: UpdateBusiness = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id(id = businessId)) {
            setBody(updateModel())
        }

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should set dashboard business`() = routeTest {
        given()
        val useCase: SetDashboardBusiness = mockk()
        coEvery { useCase.invoke(userId, businessId) } returns Result.success(Unit)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id.Dashboard(parent = BusinessRouting.Api.Business.Id(id = businessId)))

        then()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should return not found when user is not allowed to select the business as dashboard business`() = routeTest {
        given()
        val useCase: SetDashboardBusiness = mockk()
        coEvery { useCase.invoke(userId, businessId) } returns Result.failure(Error.OperationNotAllowed())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id.Dashboard(parent = BusinessRouting.Api.Business.Id(id = businessId)))

        then()
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return unauthorized when setting dashboard business without authentication`() = routeTest {
        given()
        val useCase: SetDashboardBusiness = mockk()

        setupApplication(
            extension = { install(Authentication) { bearer { authenticate { null } } } },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id.Dashboard(parent = BusinessRouting.Api.Business.Id(id = businessId)))

        then()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return unprocessable entity when creating a business with an unsupported currency`() = routeTest {
        given()
        val useCase: CreateBusiness = mockk()
        coEvery { useCase.invoke(userId, any()) } returns Result.failure(CreateBusiness.Error.InvalidCurrency())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(BusinessRouting.Api.Business()) {
            setBody(BusinessCreateRequest("Name", "ABC", TimeZone.UTC))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_INVALID_CURRENCY, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when updating a business with an unsupported currency`() = routeTest {
        given()
        val useCase: UpdateBusiness = mockk()
        coEvery { useCase.invoke(userId, any()) } returns Result.failure(UpdateBusiness.Error.InvalidCurrency())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id(id = businessId)) {
            setBody(updateModel().copy(currencyCode = "EUR"))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_INVALID_CURRENCY, response.body<SimpleServerError>().errorCode)
    }

    @Test
    fun `should return unprocessable entity when changing the currency of a business that has services`() = routeTest {
        given()
        val useCase: UpdateBusiness = mockk()
        coEvery { useCase.invoke(userId, any()) } returns Result.failure(UpdateBusiness.Error.CurrencyLocked())

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { it.principal(AppPrincipal(Uuid.random(), userId, Uuid.random())) }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { businessCrud() }
        )

        whenn()
        val client = createTestClient()
        val response = client.put(BusinessRouting.Api.Business.Id(id = businessId)) {
            setBody(updateModel().copy(currencyCode = "EUR"))
        }

        then()
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(BusinessErrorCodes.BUSINESS_CURRENCY_LOCKED, response.body<SimpleServerError>().errorCode)
    }
}

package com.bookk.business.domain.impl.di

import com.bookk.business.domain.api.BUSINESS_SCHEMA
import com.bookk.business.domain.api.BUSINESS_SERVICE_NAME
import com.bookk.business.domain.api.appointment.operation.GetAppointmentBookingContext
import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
import com.bookk.business.domain.api.business.operation.CreateBusiness
import com.bookk.business.domain.api.business.operation.DeleteBusiness
import com.bookk.business.domain.api.business.operation.DeleteDayOffsInThePast
import com.bookk.business.domain.api.business.operation.GetBusinessById
import com.bookk.business.domain.api.business.operation.GetBusinessDetails
import com.bookk.business.domain.api.business.operation.GetBusinessPermission
import com.bookk.business.domain.api.business.operation.GetDashboardBusiness
import com.bookk.business.domain.api.business.operation.GetUserBusinesses
import com.bookk.business.domain.api.business.operation.SetDashboardBusiness
import com.bookk.business.domain.api.business.operation.UpdateBusiness
import com.bookk.business.domain.api.client.operation.CreateClient
import com.bookk.business.domain.api.client.operation.DeleteClient
import com.bookk.business.domain.api.client.operation.GetClientBusinessIds
import com.bookk.business.domain.api.client.operation.GetClients
import com.bookk.business.domain.api.client.operation.UpdateClient
import com.bookk.business.domain.api.employee.operation.CreateEmployeeInvitation
import com.bookk.business.domain.api.employee.operation.DeleteProcessedEmployeeInvitations
import com.bookk.business.domain.api.employee.operation.ExpireEmployeeInvitations
import com.bookk.business.domain.api.employee.operation.GetEmployeeInvitations
import com.bookk.business.domain.api.employee.operation.GetEmployees
import com.bookk.business.domain.api.employee.operation.JoinBusiness
import com.bookk.business.domain.api.employee.operation.RevokeEmployeeInvitation
import com.bookk.business.domain.api.employee.operation.SetEmployeePermissions
import com.bookk.business.domain.api.employee.operation.SetEmployeeSuspension
import com.bookk.business.domain.api.employee.operation.UpdateEmployee
import com.bookk.business.domain.api.service.operation.CreateService
import com.bookk.business.domain.api.service.operation.CreateServiceGroup
import com.bookk.business.domain.api.service.operation.DeleteService
import com.bookk.business.domain.api.service.operation.DeleteServiceGroup
import com.bookk.business.domain.api.service.operation.GetServiceGroups
import com.bookk.business.domain.api.service.operation.GetServices
import com.bookk.business.domain.api.service.operation.GetServicesByIds
import com.bookk.business.domain.api.service.operation.IssueServiceQuote
import com.bookk.business.domain.api.service.operation.UpdateService
import com.bookk.business.domain.api.user.operation.AnonymizeUserProfile
import com.bookk.business.domain.api.user.operation.SyncUserProfile
import com.bookk.business.domain.impl.event.BusinessEventHandlerImpl
import com.bookk.business.domain.impl.operation.appointment.GetAppointmentBookingContextImpl
import com.bookk.business.domain.impl.operation.appointment.GetAppointmentRescheduleContextImpl
import com.bookk.business.domain.impl.operation.business.CreateBusinessImpl
import com.bookk.business.domain.impl.operation.business.DeleteBusinessImpl
import com.bookk.business.domain.impl.operation.business.DeleteDayOffsInThePastImpl
import com.bookk.business.domain.impl.operation.business.GetBusinessByIdImpl
import com.bookk.business.domain.impl.operation.business.GetBusinessDetailsImpl
import com.bookk.business.domain.impl.operation.business.GetBusinessPermissionImpl
import com.bookk.business.domain.impl.operation.business.GetDashboardBusinessImpl
import com.bookk.business.domain.impl.operation.business.GetUserBusinessesImpl
import com.bookk.business.domain.impl.operation.business.SetDashboardBusinessImpl
import com.bookk.business.domain.impl.operation.business.UpdateBusinessImpl
import com.bookk.business.domain.impl.operation.client.CreateClientImpl
import com.bookk.business.domain.impl.operation.client.DeleteClientImpl
import com.bookk.business.domain.impl.operation.client.GetClientBusinessIdsImpl
import com.bookk.business.domain.impl.operation.client.GetClientsImpl
import com.bookk.business.domain.impl.operation.client.UpdateClientImpl
import com.bookk.business.domain.impl.operation.employee.CreateEmployeeInvitationImpl
import com.bookk.business.domain.impl.operation.employee.DeleteProcessedEmployeeInvitationsImpl
import com.bookk.business.domain.impl.operation.employee.ExpireEmployeeInvitationsImpl
import com.bookk.business.domain.impl.operation.employee.GetEmployeeInvitationsImpl
import com.bookk.business.domain.impl.operation.employee.GetEmployeesImpl
import com.bookk.business.domain.impl.operation.employee.JoinBusinessImpl
import com.bookk.business.domain.impl.operation.employee.RevokeEmployeeInvitationImpl
import com.bookk.business.domain.impl.operation.employee.SetEmployeePermissionsImpl
import com.bookk.business.domain.impl.operation.employee.SetEmployeeSuspensionImpl
import com.bookk.business.domain.impl.operation.employee.UpdateEmployeeImpl
import com.bookk.business.domain.impl.operation.service.CreateServiceGroupImpl
import com.bookk.business.domain.impl.operation.service.CreateServiceImpl
import com.bookk.business.domain.impl.operation.service.DeleteServiceGroupImpl
import com.bookk.business.domain.impl.operation.service.DeleteServiceImpl
import com.bookk.business.domain.impl.operation.service.GetServiceGroupsImpl
import com.bookk.business.domain.impl.operation.service.GetServicesByIdsImpl
import com.bookk.business.domain.impl.operation.service.GetServicesImpl
import com.bookk.business.domain.impl.operation.service.IssueQuoteImpl
import com.bookk.business.domain.impl.operation.service.UpdateServiceImpl
import com.bookk.business.domain.impl.operation.user.AnonymizeUserProfileImpl
import com.bookk.business.domain.impl.operation.user.SyncUserProfileImpl
import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.server.user.client.di.userClientModule
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val BusinessScope: Qualifier = named(BUSINESS_SCHEMA)

fun businessDomainModule() = module {
    includes(userClientModule(BusinessScope, BUSINESS_SERVICE_NAME))
    scope(BusinessScope) {
        scopedOf(::GetBusinessByIdImpl) bind GetBusinessById::class
        scopedOf(::GetBusinessDetailsImpl) bind GetBusinessDetails::class
        scopedOf(::CreateBusinessImpl) bind CreateBusiness::class
        scopedOf(::DeleteBusinessImpl) bind DeleteBusiness::class
        scopedOf(::GetBusinessPermissionImpl) bind GetBusinessPermission::class
        scopedOf(::GetDashboardBusinessImpl) bind GetDashboardBusiness::class
        scopedOf(::SetDashboardBusinessImpl) bind SetDashboardBusiness::class
        scopedOf(::UpdateBusinessImpl) bind UpdateBusiness::class
        scopedOf(::GetUserBusinessesImpl) bind GetUserBusinesses::class
        scopedOf(::DeleteDayOffsInThePastImpl) bind DeleteDayOffsInThePast::class
        scopedOf(::BusinessEventHandlerImpl) bind EventHandler::class
        scopedOf(::CreateClientImpl) bind CreateClient::class
        scopedOf(::GetClientsImpl) bind GetClients::class
        scopedOf(::GetClientBusinessIdsImpl) bind GetClientBusinessIds::class
        scopedOf(::DeleteClientImpl) bind DeleteClient::class
        scopedOf(::UpdateClientImpl) bind UpdateClient::class
        scopedOf(::SyncUserProfileImpl) bind SyncUserProfile::class
        scopedOf(::AnonymizeUserProfileImpl) bind AnonymizeUserProfile::class
        scopedOf(::CreateServiceImpl) bind CreateService::class
        scopedOf(::DeleteServiceGroupImpl) bind DeleteServiceGroup::class
        scopedOf(::CreateServiceGroupImpl) bind CreateServiceGroup::class
        scopedOf(::DeleteServiceImpl) bind DeleteService::class
        scopedOf(::UpdateServiceImpl) bind UpdateService::class
        scopedOf(::GetServicesImpl) bind GetServices::class
        scopedOf(::GetServicesByIdsImpl) bind GetServicesByIds::class
        scopedOf(::GetServiceGroupsImpl) bind GetServiceGroups::class
        scopedOf(::IssueQuoteImpl) bind IssueServiceQuote::class
        scopedOf(::CreateEmployeeInvitationImpl) bind CreateEmployeeInvitation::class
        scopedOf(::JoinBusinessImpl) bind JoinBusiness::class
        scopedOf(::GetEmployeeInvitationsImpl) bind GetEmployeeInvitations::class
        scopedOf(::GetEmployeesImpl) bind GetEmployees::class
        scopedOf(::UpdateEmployeeImpl) bind UpdateEmployee::class
        scopedOf(::SetEmployeePermissionsImpl) bind SetEmployeePermissions::class
        scopedOf(::SetEmployeeSuspensionImpl) bind SetEmployeeSuspension::class
        scopedOf(::RevokeEmployeeInvitationImpl) bind RevokeEmployeeInvitation::class
        scopedOf(::ExpireEmployeeInvitationsImpl) bind ExpireEmployeeInvitations::class
        scopedOf(::DeleteProcessedEmployeeInvitationsImpl) bind DeleteProcessedEmployeeInvitations::class
        scopedOf(::GetAppointmentBookingContextImpl) bind GetAppointmentBookingContext::class
        scopedOf(::GetAppointmentRescheduleContextImpl) bind GetAppointmentRescheduleContext::class
    }
}

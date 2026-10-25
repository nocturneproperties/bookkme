package com.bookk.business.microservice.route

import com.bookk.business.domain.impl.di.BusinessScope
import com.bookk.business.microservice.route.api.businessCrud
import com.bookk.business.microservice.route.api.clientCrud
import com.bookk.business.microservice.route.api.employeeCrud
import com.bookk.business.microservice.route.api.employeeInvitationCrud
import com.bookk.business.microservice.route.api.healthCheck
import com.bookk.business.microservice.route.api.internal.getAppointmentBookingContext
import com.bookk.business.microservice.route.api.internal.getAppointmentRescheduleContext
import com.bookk.business.microservice.route.api.internal.getBusinessDetails
import com.bookk.business.microservice.route.api.internal.getBusinessPermission
import com.bookk.business.microservice.route.api.internal.getClientBusinessIds
import com.bookk.business.microservice.route.api.internal.getServicesByIds
import com.bookk.business.microservice.route.api.quote
import com.bookk.business.microservice.route.api.serviceCrud
import com.bookk.business.microservice.route.api.serviceGroupCrud
import io.ktor.server.routing.Routing
import library.signing.route.jwks


fun Routing.businessRoute() {
    healthCheck()
    jwks(BusinessScope)
    businessCrud()
    getBusinessDetails()
    getBusinessPermission()
    getAppointmentBookingContext()
    getAppointmentRescheduleContext()
    getServicesByIds()
    getClientBusinessIds()
    clientCrud()
    serviceCrud()
    serviceGroupCrud()
    quote()
    employeeInvitationCrud()
    employeeCrud()
}
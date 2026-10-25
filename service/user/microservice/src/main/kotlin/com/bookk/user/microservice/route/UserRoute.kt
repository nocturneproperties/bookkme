package com.bookk.user.microservice.route

import com.bookk.user.microservice.route.api.getCurrentUser
import com.bookk.user.microservice.route.api.getHealthCheck
import com.bookk.user.microservice.route.api.internal.getUserByEmail
import com.bookk.user.microservice.route.api.internal.getUserById
import com.bookk.user.microservice.route.api.internal.postCreateUser
import com.bookk.user.microservice.route.api.patchUser
import com.bookk.user.microservice.route.api.postContactForm
import io.ktor.server.routing.Routing


fun Routing.userRoute() {
    getHealthCheck()
    getUserById()
    getUserByEmail()
    postCreateUser()
    getCurrentUser()
    patchUser()
    postContactForm()
}
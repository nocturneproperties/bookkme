package com.bookk.core.service

import com.bookk.core.AppLevelConstants
import com.bookk.core.safeCapitalize

class ServiceConfig(
    val version: String = AppLevelConstants.serviceVersion,
    val title: String = "${AppLevelConstants.serviceName.safeCapitalize()}Microservice",
    val root: String = "api/${AppLevelConstants.serviceName}"
)
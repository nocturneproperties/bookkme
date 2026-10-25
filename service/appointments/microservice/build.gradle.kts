plugins {
    alias(libs.plugins.bookk.microservice)
}

group = "com.bookk.server.microservice.appointments"

application {
    mainClass.set("com.bookk.appointments.microservice.AppointmentsMicroserviceKt")
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = false
        onlyCommented = false
    }
}

dependencies {
    implementation(projects.service.business.client)
    implementation(projects.core)
    implementation(projects.core.service)
    implementation(projects.core.domain)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.impl)
    implementation(projects.core.data.cache.api)
    implementation(projects.core.data.cache.impl)
    implementation(projects.library.signing.api)
    implementation(projects.service.appointments.data)
    implementation(projects.service.appointments.domain.api)
    implementation(projects.service.appointments.domain.impl)
    implementation(projects.library.scheduler)
    testImplementation(libs.joda.money)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.service))
    testImplementation(testFixtures(projects.library.scheduler))
}
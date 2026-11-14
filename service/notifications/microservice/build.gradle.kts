plugins {
    alias(libs.plugins.bookk.microservice)
}

group = "com.bookk.server.microservice.notifications"

application {
    mainClass.set("com.bookk.notifications.microservice.NotificationsMicroserviceKt")
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = false
        onlyCommented = false
    }
}

dependencies {
    implementation(libs.firebase.admin)

    implementation(projects.core)
    implementation(projects.core.service)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.impl)
    implementation(projects.core.data.cache.impl)
    implementation(projects.service.notifications.data)
    implementation(projects.service.notifications.domain.api)
    implementation(projects.service.notifications.client)
    implementation(projects.service.notifications.domain.impl)
    implementation(projects.library.email.impl)
    implementation(projects.library.scheduler)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.library.outbox))
    testImplementation(testFixtures(projects.library.encryption.impl))
    testImplementation(testFixtures(projects.core.service))
    testImplementation(testFixtures(projects.library.scheduler))
}

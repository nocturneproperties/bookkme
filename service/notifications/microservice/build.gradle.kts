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
    implementation(projects.core.domain)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.impl)
    implementation(projects.core.data.cache.api)
    implementation(projects.core.data.cache.impl)
    implementation(projects.service.notifications.data)
    implementation(projects.service.notifications.domain.api)
    implementation(projects.service.notifications.domain.impl)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.service))
}

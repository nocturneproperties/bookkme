plugins {
    alias(libs.plugins.bookk.microservice)
}

group = "com.bookk.server.microservice.business"

application {
    mainClass.set("com.bookk.business.BusinessMicroserviceKt")
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = false
        onlyCommented = false
    }
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.service)
    implementation(projects.core.domain)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.impl)
    implementation(projects.core.data.eventstreaming.data)
    implementation(projects.core.data.cache.api)
    implementation(projects.core.data.cache.impl)
    implementation(projects.library.signing.api)
    implementation(projects.library.signing.impl)
    implementation(projects.library.signing.route)
    implementation(projects.library.scheduler)
    implementation(projects.service.business.data)
    implementation(projects.service.business.domain.api)
    implementation(projects.service.business.domain.impl)
    testImplementation(libs.joda.money)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.service))
    testImplementation(testFixtures(projects.core.data.eventstreaming.data))
    testImplementation(testFixtures(projects.library.scheduler))
}
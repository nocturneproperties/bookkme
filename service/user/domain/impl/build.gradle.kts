plugins {
    alias(libs.plugins.bookk.domain.impl)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlin.coroutines)
    implementation(libs.ktor.jwt)
    implementation(projects.core)
    implementation(projects.core.domain)
    implementation(projects.core.domain.datasource)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.service.user.domain.api)
    implementation(projects.service.user.data.source)
    implementation(projects.service.user.client)
    implementation(projects.service.authorization.client)
    implementation(projects.service.business.client)
    implementation(projects.service.appointments.client)
    implementation(projects.service.notifications.client)
    implementation(projects.library.stepup.api)
    implementation(projects.library.stepup.impl)
    implementation(projects.library.export.api)
    testImplementation(testFixtures(projects.core))
    testImplementation(projects.core.data.eventstreaming.impl)
    testImplementation(testFixtures(projects.core.data.eventstreaming.impl))
    testImplementation(testFixtures(projects.core.domain.datasource))
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(testFixtures(projects.library.stepup.impl))
}

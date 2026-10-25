plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.data)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.data)
    implementation(projects.core.data.cache.api)
    implementation(projects.core.domain.datasource)
    implementation(projects.service.appointments.domain.api)
    implementation(projects.service.appointments.data.source)
    implementation(libs.joda.money)
    implementation(libs.passkey)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(testFixtures(projects.core.data.cache.impl))
    testImplementation(libs.joda.money)
}
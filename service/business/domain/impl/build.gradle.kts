plugins {
    alias(libs.plugins.bookk.domain.impl)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlin.serialization.json)
    implementation(projects.core)
    implementation(projects.core.domain)
    implementation(projects.core.domain.datasource)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.service.business.domain.api)
    implementation(projects.service.business.client)
    implementation(projects.service.user.client)
    implementation(projects.service.authorization.client)
    implementation(projects.service.appointments.client)
    implementation(projects.service.business.data.source)
    implementation(projects.library.permissions)
    implementation(projects.library.signing.api)
    implementation(projects.library.actionconfirmation.api)
    implementation(projects.library.export.api)
    implementation(projects.library.validation)
    testImplementation(testFixtures(projects.core))
    testImplementation(projects.core.data.eventstreaming.impl)
    testImplementation(testFixtures(projects.core.data.eventstreaming.impl))
    testImplementation(testFixtures(projects.core.domain.datasource))
    testImplementation(testFixtures(projects.library.actionconfirmation.impl))
}

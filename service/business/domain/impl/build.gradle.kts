plugins {
    alias(libs.plugins.bookk.domain.impl)
}

dependencies {
    implementation(libs.kotlin.coroutines)
    implementation(projects.core)
    implementation(projects.core.domain)
    implementation(projects.core.domain.datasource)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.service.business.domain.api)
    implementation(projects.service.business.client)
    implementation(projects.service.user.client)
    implementation(projects.service.authorization.client)
    implementation(projects.service.business.data.source)
    implementation(projects.library.permissions)
    implementation(projects.library.signing.api)
    implementation(projects.library.validation)
    testImplementation(testFixtures(projects.core))
    testImplementation(projects.core.data.eventstreaming.impl)
    testImplementation(testFixtures(projects.core.data.eventstreaming.impl))
    testImplementation(testFixtures(projects.core.domain.datasource))
}
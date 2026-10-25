plugins {
    alias(libs.plugins.bookk.domain.impl)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.domain)
    implementation(projects.core.domain.datasource)
    implementation(projects.core.i18n)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.service.authorization.client)
    implementation(projects.service.appointments.client)
    implementation(projects.service.business.client)
    implementation(projects.service.user.client)
    implementation(projects.service.notifications.domain.api)
    implementation(projects.service.notifications.data.source)
    implementation(projects.library.permissions)
    implementation(libs.firebase.admin)
    testImplementation(testFixtures(projects.core))
    testImplementation(projects.core.data.eventstreaming.impl)
    testImplementation(testFixtures(projects.core.data.eventstreaming.impl))
    testImplementation(testFixtures(projects.core.domain.datasource))
    testImplementation(libs.kotlin.coroutines.test)
}

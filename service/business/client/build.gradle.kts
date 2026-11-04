plugins {
    alias(libs.plugins.bookk.client)
}

dependencies {
    api(libs.kotlin.datetime)
    implementation(projects.core.client)
    api(projects.core.domain)
    api(projects.core.data.eventstreaming.api)
    api(projects.service.business.domain.api)
    api(projects.library.schedule)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.ktor.client.mock)
}

plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    api(libs.kotlin.datetime)
    testImplementation(testFixtures(projects.core))
}

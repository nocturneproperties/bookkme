plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(libs.joda.money)
    testImplementation(testFixtures(projects.core))
}

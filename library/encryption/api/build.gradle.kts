plugins {
    alias(libs.plugins.bookk.kotlin)
    alias(libs.plugins.kotlin.fixtures)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(testFixtures(projects.core))
}

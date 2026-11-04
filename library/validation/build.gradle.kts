plugins {
    alias(libs.plugins.bookk.kotlin)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.joda.money)
    testImplementation(libs.kotlin.test)
    testImplementation(testFixtures(projects.core))
}

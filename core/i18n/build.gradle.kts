plugins {
    alias(libs.plugins.bookk.kotlin)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.icu4j)
    testImplementation(libs.kotlin.test)
}

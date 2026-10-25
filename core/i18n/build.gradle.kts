plugins {
    alias(libs.plugins.kotlin.jvm)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.icu4j)
    testImplementation(libs.kotlin.test)
}

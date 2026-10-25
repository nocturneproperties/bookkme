plugins {
    alias(libs.plugins.bookk.microservice)
}

group = "com.bookk.server.microservice.user"

application {
    mainClass.set("com.bookk.user.UserMicroserviceKt")
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.service)
    implementation(projects.core.domain)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.impl)
    implementation(projects.core.data.cache.api)
    implementation(projects.core.data.cache.impl)
    implementation(projects.service.user.data)
    implementation(projects.service.user.domain.api)
    implementation(projects.service.user.domain.impl)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.service))
}
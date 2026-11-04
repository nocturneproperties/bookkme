plugins {
    alias(libs.plugins.bookk.microservice)
}

group = "com.bookk.server.microservice.user"

application {
    mainClass.set("com.bookk.user.UserMicroserviceKt")
}

dependencies {
    implementation(projects.library.stepup.impl)
    implementation(projects.core.service)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.impl)
    implementation(projects.core.data.eventstreaming.data)
    implementation(projects.core.data.cache.impl)
    implementation(projects.service.user.data)
    implementation(projects.service.user.domain.api)
    implementation(projects.service.user.domain.impl)
    implementation(projects.library.export.api)
    implementation(projects.library.export.route)
    implementation(projects.library.scheduler)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data.eventstreaming.data))
    testImplementation(testFixtures(projects.core.service))
    testImplementation(testFixtures(projects.library.scheduler))
    testImplementation(projects.library.signing.api)
}

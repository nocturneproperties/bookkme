import com.bookk.build_src.tools.includeLocalProperties
import com.google.cloud.tools.jib.gradle.BuildDockerTask
import com.google.cloud.tools.jib.gradle.JibTask

plugins {
    alias(libs.plugins.ktor).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.kotlin.serialization).apply(false)
    alias(libs.plugins.bookk.kotlin).apply(false)
    alias(libs.plugins.bookk.data).apply(false)
    alias(libs.plugins.bookk.domain.api).apply(false)
    alias(libs.plugins.bookk.domain.impl).apply(false)
    alias(libs.plugins.bookk.client).apply(false)
    alias(libs.plugins.bookk.microservice)
}

group = "com.bookk.server"
version = "0.15.12"

application {
    mainClass.set("com.bookk.server.MonolithServerKt")
}

subprojects {
    plugins.withId("com.bookk.microservice") {
        version = rootProject.version
    }

    val uniqueArchivesName = path.removePrefix(":").replace(":", "-")
    plugins.withType<BasePlugin> {
        configure<BasePluginExtension> {
            archivesName = uniqueArchivesName
        }
    }
}

dependencies {
    implementation(projects.core.service)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.impl)
    implementation(projects.library.scheduler)
    implementation(projects.service.authorization.microservice)
    implementation(projects.service.authorization.domain.impl)
    implementation(projects.service.user.microservice)
    implementation(projects.service.user.domain.impl)
    implementation(projects.service.business.microservice)
    implementation(projects.service.business.domain.impl)
    implementation(projects.service.appointments.microservice)
    implementation(projects.service.appointments.domain.impl)
    implementation(projects.service.notifications.microservice)
    implementation(projects.service.notifications.domain.impl)
    testImplementation(projects.library.signing.api)
    testImplementation(projects.service.authorization.domain.api)
    testImplementation(projects.service.business.domain.api)
    testImplementation(projects.service.appointments.domain.api)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.service))
    testImplementation(testFixtures(projects.library.scheduler))
}

includeLocalProperties(providers.gradleProperty("local.propertiesFile").get())

tasks.withType<JibTask> {
    notCompatibleWithConfigurationCache("because of https://github.com/GoogleContainerTools/jib/issues/3132")
}

tasks.withType<BuildDockerTask> {
    notCompatibleWithConfigurationCache("because of https://github.com/GoogleContainerTools/jib/issues/3132")
}

subprojects {
    tasks.withType<Test> {
        environment(
            mapOf(
                "APPLICATION_SERVICE_NAME" to "test",
                "APPLICATION_SERVICE_VERSION" to "test"
            )
        )
    }
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    includeBuild("build-src")
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        google()
        mavenLocal()
    }
}

rootProject.name = "bookk-server"

include(":deployment")

//Core
include(":core")
include(":core:domain")
include(":core:domain:datasource")
include(":core:i18n")
include(":core:data")
include(":core:data:cache:api")
include(":core:data:cache:impl")
include(":core:data:eventstreaming:api")
include(":core:data:eventstreaming:impl")
include(":core:data:eventstreaming:data")
include(":core:service")
include(":core:client")

//Library
include(":library:money")
include(":library:idempotency")
include(":library:permissions")
include(":library:validation")
include(":library:scheduler")
include(":library:schedule")
include(":library:signing:api")
include(":library:signing:impl")
include(":library:signing:route")

//Authorization
include(":service:authorization:microservice")
include(":service:authorization:domain:api")
include(":service:authorization:domain:impl")
include(":service:authorization:data:source")
include(":service:authorization:data")
include(":service:authorization:client")

//User
include(":service:user:microservice")
include(":service:user:domain:api")
include(":service:user:domain:impl")
include(":service:user:data:source")
include(":service:user:data")
include(":service:user:client")

//Business
include(":service:business:microservice")
include(":service:business:domain:api")
include(":service:business:domain:impl")
include(":service:business:data:source")
include(":service:business:data")
include(":service:business:client")

//Appointments
include(":service:appointments:microservice")
include(":service:appointments:domain:api")
include(":service:appointments:domain:impl")
include(":service:appointments:data:source")
include(":service:appointments:data")
include(":service:appointments:client")

//Notifications
include(":service:notifications:microservice")
include(":service:notifications:domain:api")
include(":service:notifications:domain:impl")
include(":service:notifications:data:source")
include(":service:notifications:data")
include(":service:notifications:client")


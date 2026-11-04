plugins {
    alias(libs.plugins.bookk.kotlin)
}

dependencies {
    api(projects.service.appointments.domain.api)
}

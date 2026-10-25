package library.signing.impl

object TokenConstants {
    val authServiceHostname: String
        get() = System.getenv("APPLICATION_AUTH_SERVICE_HOSTNAME")
    val businessServiceHostname: String
        get() = System.getenv("APPLICATION_BUSINESS_SERVICE_HOSTNAME")
    val appointmentsServiceHostname: String
        get() = System.getenv("APPLICATION_APPOINTMENTS_SERVICE_HOSTNAME")
}
package library.email.impl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PostmarkResponse(
    @SerialName("ErrorCode") val errorCode: Int = 0,
    @SerialName("Message") val message: String = "",
)

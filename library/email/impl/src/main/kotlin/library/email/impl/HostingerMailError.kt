package library.email.impl

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class HostingerMailError(
    val code: String = "",
    val error: String = "",
    val params: JsonElement = JsonObject(emptyMap()),
) {
    val refusedFields: Set<String>
        get() {
            return (params as? JsonObject)?.keys.orEmpty()
        }
}

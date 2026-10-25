package library.permissions

import com.bookk.core.domain.entity.Error
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

enum class PermissionAction {
    VIEW,
    UPDATE,
    DELETE
}

@Serializable
data class ResourcePermission(
    @ProtoNumber(1)
    val view: Boolean,
    @ProtoNumber(2)
    val update: Boolean,
    @ProtoNumber(3)
    val delete: Boolean
) {
    fun grants(action: PermissionAction): Boolean = when (action) {
        PermissionAction.VIEW -> view
        PermissionAction.UPDATE -> update
        PermissionAction.DELETE -> delete
    }

    fun covers(other: ResourcePermission): Boolean =
        (view || !other.view) && (update || !other.update) && (delete || !other.delete)

    companion object {
        val NONE = ResourcePermission(view = false, update = false, delete = false)
        val FULL = ResourcePermission(view = true, update = true, delete = true)
    }
}

fun ResourcePermission?.assert(action: PermissionAction) {
    if (this?.grants(action) != true) throw Error.OperationNotAllowed()
}

fun ResourcePermission?.assertOrSelf(action: PermissionAction, actorId: Uuid, assigneeId: Uuid) {
    if (this?.grants(action) == true) return
    if (this?.view == true && actorId == assigneeId) return
    throw Error.OperationNotAllowed()
}

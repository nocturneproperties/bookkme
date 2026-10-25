package library.permissions

import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ResourcePermissionTest {

    @Test
    fun `should grant the action it was given`() = runUnitTest {
        given()
        val permission = ResourcePermission(view = true, update = false, delete = false)

        whenn()
        val grantsView = permission.grants(PermissionAction.VIEW)
        val grantsUpdate = permission.grants(PermissionAction.UPDATE)

        then()
        assertTrue(grantsView)
        assertFalse(grantsUpdate)
    }

    @Test
    fun `should cover a permission with fewer or equal bits set`() = runUnitTest {
        given()
        val full = ResourcePermission.FULL
        val partial = ResourcePermission(view = true, update = true, delete = false)

        whenn()
        val result = full.covers(partial)

        then()
        assertTrue(result)
    }

    @Test
    fun `should not cover a permission with a bit the holder lacks`() = runUnitTest {
        given()
        val holder = ResourcePermission(view = true, update = true, delete = false)
        val requested = ResourcePermission(view = true, update = false, delete = true)

        whenn()
        val result = holder.covers(requested)

        then()
        assertFalse(result)
    }

    @Test
    fun `should allow asserting a granted action`() = runUnitTest {
        given()
        val permission = ResourcePermission(view = false, update = true, delete = false)

        whenn()
        val result = runCatching { permission.assert(PermissionAction.UPDATE) }

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should reject asserting an action that was not granted`() = runUnitTest {
        given()
        val permission = ResourcePermission(view = true, update = false, delete = false)

        whenn()
        val result = runCatching { permission.assert(PermissionAction.UPDATE) }

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should reject asserting an action when there is no grant at all`() = runUnitTest {
        given()

        whenn()
        val result = runCatching { null.assert(PermissionAction.VIEW) }

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should allow a granted action regardless of assignee`() = runUnitTest {
        given()
        val actorId = Uuid.random()
        val assigneeId = Uuid.random()
        val permission = ResourcePermission(view = false, update = true, delete = false)

        whenn()
        val result = runCatching { permission.assertOrSelf(PermissionAction.UPDATE, actorId = actorId, assigneeId = assigneeId) }

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should allow a view holder acting on their own resource`() = runUnitTest {
        given()
        val actorId = Uuid.random()
        val permission = ResourcePermission(view = true, update = false, delete = false)

        whenn()
        val result = runCatching { permission.assertOrSelf(PermissionAction.UPDATE, actorId = actorId, assigneeId = actorId) }

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should reject a view holder acting on someone elses resource`() = runUnitTest {
        given()
        val actorId = Uuid.random()
        val assigneeId = Uuid.random()
        val permission = ResourcePermission(view = true, update = false, delete = false)

        whenn()
        val result = runCatching { permission.assertOrSelf(PermissionAction.UPDATE, actorId = actorId, assigneeId = assigneeId) }

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should reject a missing grant even when acting on their own resource`() = runUnitTest {
        given()
        val actorId = Uuid.random()

        whenn()
        val result = runCatching { null.assertOrSelf(PermissionAction.UPDATE, actorId = actorId, assigneeId = actorId) }

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }
}

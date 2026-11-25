package com.bookk.core.data

import com.bookk.core.domain.entity.PageCursor
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import kotlin.time.Instant
import kotlin.uuid.Uuid

class Keyset(
    private val sortKey: Column<Instant>,
    private val id: Column<EntityID<Uuid>>,
    private val order: SortOrder,
) {
    init {
        require(order == SortOrder.ASC || order == SortOrder.DESC) { "A keyset is ordered ASC or DESC" }
    }

    fun after(cursor: PageCursor?): Op<Boolean> {
        if (cursor == null) return Op.TRUE
        val sameSortKey = sortKey eq cursor.after
        return if (order == SortOrder.DESC) {
            (sortKey less cursor.after) or (sameSortKey and (id less cursor.id))
        } else {
            (sortKey greater cursor.after) or (sameSortKey and (id greater cursor.id))
        }
    }

    fun orderBy(): Array<Pair<Expression<*>, SortOrder>> {
        return arrayOf(sortKey to order, id to order)
    }
}

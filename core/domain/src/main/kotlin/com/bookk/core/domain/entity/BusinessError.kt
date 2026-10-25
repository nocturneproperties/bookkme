package com.bookk.core.domain.entity

open class BusinessError(
    val statusCode: Int,
    val code: Int,
    override val message: String
) : Throwable() {

    protected fun readResolve(): Any = this
}
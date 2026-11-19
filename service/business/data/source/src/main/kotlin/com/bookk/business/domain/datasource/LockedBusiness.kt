package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.business.entity.BusinessLimits

data class LockedBusiness(val currencyCode: String, val limits: BusinessLimits) {
    companion object {
        fun stub(currencyCode: String = "USD", limits: BusinessLimits = BusinessLimits.stub()): LockedBusiness {
            return LockedBusiness(currencyCode, limits)
        }
    }
}

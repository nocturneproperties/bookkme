package library.signing

import com.bookk.core.AppLevelConstants

object TokenAudience {
    val access: String
        get() = AppLevelConstants.domainName

    val stepUp: String
        get() = "${AppLevelConstants.domainName}/step-up"
}

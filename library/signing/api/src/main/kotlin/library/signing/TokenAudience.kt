package library.signing

import com.bookk.core.AppLevelConstants

object TokenAudience {
    val access: String
        get() = AppLevelConstants.domainName

    val actionConfirmation: String
        get() = "${AppLevelConstants.domainName}/action-confirmation"
}

package library.permissions

import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

class EmployeeAccessSuspended : BusinessError(
    statusCode = HttpStatusCode.Forbidden.value,
    code = PermissionErrorCodes.EMPLOYEE_ACCESS_SUSPENDED,
    message = "Your access to this business is suspended"
)

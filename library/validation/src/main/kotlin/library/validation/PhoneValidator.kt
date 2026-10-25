package library.validation

object PhoneValidator {
    const val DEFAULT_MIN_LENGTH = 3
    const val DEFAULT_MAX_LENGTH = 32

    private val ALLOWED_CHARACTERS_REGEX = Regex("^\\+?[0-9()\\-\\s]+$")

    fun isValid(phone: String, minLength: Int = DEFAULT_MIN_LENGTH, maxLength: Int = DEFAULT_MAX_LENGTH): Boolean =
        phone.length in minLength..maxLength &&
            ALLOWED_CHARACTERS_REGEX.matches(phone) &&
            phone.any(Char::isDigit)
}

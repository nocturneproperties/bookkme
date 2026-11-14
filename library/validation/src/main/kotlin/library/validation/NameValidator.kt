package library.validation

object NameValidator {
    const val DEFAULT_MIN_LENGTH = 2
    const val DEFAULT_MAX_LENGTH = 512

    fun isValid(name: String, minLength: Int = DEFAULT_MIN_LENGTH, maxLength: Int = DEFAULT_MAX_LENGTH): Boolean =
        name.length in minLength..maxLength
}

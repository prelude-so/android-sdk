package so.prelude.android.sdk.request

/*
 * Simplified response type to check the result of a network request.
 */
sealed class NetworkResponse(
    open val code: Int,
) {
    class Success(
        override val code: Int,
        val body: ByteArray?,
    ) : NetworkResponse(code)

    class Error(
        override val code: Int,
    ) : NetworkResponse(code)
}

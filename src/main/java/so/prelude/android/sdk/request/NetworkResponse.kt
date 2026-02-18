package so.prelude.android.sdk.request

/*
 * Simplified response type to check the result of a network request.
 */
sealed class NetworkResponse(
    open val code: Int,
    open val fromPayloadRequest: Boolean,
) {
    class Success(
        override val code: Int,
        override val fromPayloadRequest: Boolean,
        val body: ByteArray?,
    ) : NetworkResponse(code, fromPayloadRequest)

    class Error(
        override val code: Int,
        override val fromPayloadRequest: Boolean,
        val message: String,
    ) : NetworkResponse(code, fromPayloadRequest)

    class Redirect(
        override val code: Int,
        override val fromPayloadRequest: Boolean,
        val location: String,
    ) : NetworkResponse(code, fromPayloadRequest)
}

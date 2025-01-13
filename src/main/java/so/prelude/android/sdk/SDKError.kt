package so.prelude.android.sdk

/**
 * SDKError is the error type for the Prelude SDK.
 */
sealed class SDKError : Exception() {
    /**
     * A configuration error.
     */
    data class ConfigurationError(
        override val message: String,
    ) : SDKError()

    /**
     * An internal error.
     */
    data class InternalError(
        override val message: String,
    ) : SDKError()

    /**
     * A request error.
     */
    data class RequestError(
        override val message: String,
    ) : SDKError()

    /**
     * A system error.
     */
    data class SystemError(
        override val message: String,
    ) : SDKError()
}

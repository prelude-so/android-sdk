package so.prelude.android.sdk.verification

import kotlinx.serialization.Serializable

@Serializable
data class VerificationResponseError(
    val status: String,
    val reason: String,
)

package so.prelude.android.sdk.verification

import kotlinx.serialization.Serializable

@Serializable
data class VerificationResponseSuccess(
    val code: String,
)

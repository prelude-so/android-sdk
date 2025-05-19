package so.prelude.android.sdk.verification

interface VerificationListener {
    fun onVerificationSuccess(code: String)

    fun onVerificationFailure()
}

package so.prelude.android.sdk

@FunctionalInterface
fun interface DispatchStatusListener {
    fun onStatus(
        status: Status,
        dispatchId: String,
    )

    enum class Status {
        STARTED,
        SUCCESS,
        FAILURE,
    }
}

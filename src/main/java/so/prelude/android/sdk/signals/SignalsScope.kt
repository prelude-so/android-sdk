package so.prelude.android.sdk.signals

enum class SignalsScope(
    val value: Int,
) {
    FULL(1),
    SILENT_VERIFICATION(2),
    ;

    companion object {
        fun from(value: Int): SignalsScope = SignalsScope.values().firstOrNull { it.value == value } ?: FULL
    }
}

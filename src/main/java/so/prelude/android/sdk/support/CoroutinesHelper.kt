package so.prelude.android.sdk.support

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal val sdkInternalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

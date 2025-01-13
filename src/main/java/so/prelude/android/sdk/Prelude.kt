package so.prelude.android.sdk

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import so.prelude.android.sdk.DispatchStatusListener.Status
import so.prelude.android.sdk.support.sdkInternalScope
import so.prelude.android.sdk.workmanager.DispatchSignalsWorker

/**
 * Initialize the Prelude SDK.
 * @param configuration the configuration for the Prelude SDK instance.
 */
data class Prelude(
    /**
     * The configuration for the Prelude SDK instance.
     */
    var configuration: Configuration,
) {
    /**
     * Initialize the Prelude SDK.
     * @param sdkKey the SDK key. (Note: you can get one from the Prelude Dashboard)
     */
    constructor(context: Context, sdkKey: String) : this(Configuration(context, sdkKey))

    @JvmOverloads
    fun dispatchSignals(dispatchStatusListener: DispatchStatusListener? = null): String {
        val dispatchId = dispatchId()
        val dispatchRequest = DispatchSignalsWorker.buildRequest(configuration, dispatchId)

        if (dispatchStatusListener != null) {
            sdkInternalScope.launch {
                WorkManager
                    .getInstance(configuration.context)
                    .getWorkInfoByIdFlow(dispatchRequest.id)
                    .filterNotNull()
                    .collectLatest {
                        when (it.state) {
                            WorkInfo.State.ENQUEUED -> Unit
                            WorkInfo.State.RUNNING -> dispatchStatusListener.onStatus(Status.STARTED, dispatchId)
                            WorkInfo.State.SUCCEEDED -> dispatchStatusListener.onStatus(Status.SUCCESS, dispatchId)
                            WorkInfo.State.FAILED -> dispatchStatusListener.onStatus(Status.FAILURE, dispatchId)
                            WorkInfo.State.BLOCKED -> dispatchStatusListener.onStatus(Status.FAILURE, dispatchId)
                            WorkInfo.State.CANCELLED -> dispatchStatusListener.onStatus(Status.FAILURE, dispatchId)
                        }
                    }
            }
        }

        WorkManager
            .getInstance(configuration.context)
            .enqueue(dispatchRequest)

        return dispatchId
    }
}

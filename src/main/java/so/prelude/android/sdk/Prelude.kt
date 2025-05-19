package so.prelude.android.sdk

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import so.prelude.android.sdk.DispatchStatusListener.Status
import so.prelude.android.sdk.signals.SignalsScope
import so.prelude.android.sdk.support.sdkInternalScope
import so.prelude.android.sdk.verification.VerificationListener
import so.prelude.android.sdk.verification.performSilentVerification
import so.prelude.android.sdk.workmanager.DispatchSignalsWorker
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

    fun dispatchSignals(dispatchStatusListener: DispatchStatusListener?): String =
        dispatchSignals(SignalsScope.FULL, dispatchStatusListener)

    @JvmOverloads
    fun dispatchSignals(
        signalsScope: SignalsScope = SignalsScope.FULL,
        dispatchStatusListener: DispatchStatusListener? = null,
    ): String {
        val dispatchId = dispatchId()
        val dispatchRequest =
            DispatchSignalsWorker.buildRequest(
                configuration = configuration,
                dispatchId = dispatchId,
                signalsScope = signalsScope,
            )
        val wm = WorkManager.getInstance(configuration.context)

        if (dispatchStatusListener != null) {
            sdkInternalScope.launch {
                wm
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

        wm.enqueue(dispatchRequest)

        return dispatchId
    }

    fun dispatchSignalsFlow(signalsScope: SignalsScope = SignalsScope.FULL): Flow<DispatchProgress> {
        val dispatchId = dispatchId()
        val dispatchRequest =
            DispatchSignalsWorker.buildRequest(
                configuration = configuration,
                dispatchId = dispatchId,
                signalsScope = signalsScope,
            )
        val wm = WorkManager.getInstance(configuration.context)

        return wm
            .getWorkInfoByIdFlow(dispatchRequest.id)
            .filterNotNull()
            .map {
                when (it.state) {
                    WorkInfo.State.ENQUEUED -> DispatchProgress(dispatchId, Status.STARTED)
                    WorkInfo.State.RUNNING -> DispatchProgress(dispatchId, Status.STARTED)
                    WorkInfo.State.SUCCEEDED -> DispatchProgress(dispatchId, Status.SUCCESS)
                    WorkInfo.State.FAILED -> DispatchProgress(dispatchId, Status.FAILURE)
                    WorkInfo.State.BLOCKED -> DispatchProgress(dispatchId, Status.FAILURE)
                    WorkInfo.State.CANCELLED -> DispatchProgress(dispatchId, Status.FAILURE)
                }
            }.onStart { wm.enqueue(dispatchRequest) }
    }

    suspend fun dispatchSignals(signalsScope: SignalsScope = SignalsScope.FULL): Result<String> =
        try {
            val progress =
                dispatchSignalsFlow(signalsScope = signalsScope)
                    .first { it.status == Status.SUCCESS || it.status == Status.FAILURE }

            when (progress.status) {
                Status.SUCCESS -> Result.success(progress.dispatchId)
                else -> Result.failure(Exception("Dispatch failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    @JvmOverloads
    fun verifySilent(
        url: URL,
        listener: VerificationListener,
        timeout: Long = 10000,
    ) {
        sdkInternalScope.launch {
            verifySilent(url, timeout.milliseconds)
                .map {
                    listener.onVerificationSuccess(it)
                }.onFailure {
                    listener.onVerificationFailure()
                }
        }
    }

    suspend fun verifySilent(
        url: URL,
        timeout: Duration = 10.seconds,
    ): Result<String> = performSilentVerification(url, timeout, configuration)

    data class DispatchProgress(
        val dispatchId: String,
        val status: Status,
    )
}

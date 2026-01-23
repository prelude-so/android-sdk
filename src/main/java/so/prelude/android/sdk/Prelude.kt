package so.prelude.android.sdk

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import so.prelude.android.sdk.DispatchStatusListener.Status
import so.prelude.android.sdk.signals.SignalsScope
import so.prelude.android.sdk.signals.dispatchSignals
import so.prelude.android.sdk.support.sdkInternalScope
import so.prelude.android.sdk.verification.VerificationListener
import so.prelude.android.sdk.verification.performSilentVerification
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
        sdkInternalScope.launch {
            try {
                val dispatchId =
                    dispatchSignals(
                        configuration = configuration,
                        signalsScope = signalsScope,
                    )
                dispatchStatusListener?.onStatus(Status.SUCCESS, dispatchId)
            } catch (e: Exception) {
                Log.e("Prelude", "Dispatch failed: ${e.message}")
                dispatchStatusListener?.onStatus(Status.FAILURE, "")
            } catch (e: LinkageError) {
                Log.e("Prelude", "Dispatch failed: ${e.message}")
                dispatchStatusListener?.onStatus(Status.FAILURE, "")
            }
        }

        return ""
    }

    fun dispatchSignalsFlow(signalsScope: SignalsScope = SignalsScope.FULL): Flow<DispatchProgress> =
        flow {
            try {
                val dispatchId =
                    dispatchSignals(
                        configuration = configuration,
                        signalsScope = signalsScope,
                    )
                emit(DispatchProgress(dispatchId, Status.SUCCESS))
            } catch (e: Exception) {
                Log.e("Prelude", "Dispatch failed: ${e.message}")
                emit(DispatchProgress("", Status.FAILURE))
            } catch (e: LinkageError) {
                Log.e("Prelude", "Dispatch failed: ${e.message}")
                emit(DispatchProgress("", Status.FAILURE))
            }
        }

    suspend fun dispatchSignals(signalsScope: SignalsScope = SignalsScope.FULL): Result<String> =
        try {
            val dispatchId =
                dispatchSignals(
                    configuration = configuration,
                    signalsScope = signalsScope,
                )
            Result.success(dispatchId)
        } catch (e: Exception) {
            Result.failure(e)
        } catch (e: LinkageError) {
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

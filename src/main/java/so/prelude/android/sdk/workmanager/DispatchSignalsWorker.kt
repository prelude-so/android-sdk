package so.prelude.android.sdk.workmanager

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import so.prelude.android.sdk.Configuration
import so.prelude.android.sdk.Endpoint
import so.prelude.android.sdk.SDKError
import so.prelude.android.sdk.signals.dispatchSignals

internal class DispatchSignalsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            val sdkKey =
                inputData.getString(SDK_KEY_PARAM)
                    ?: throw SDKError.ConfigurationError("Incorrect configuration. Missing SDK key.")
            val dispatchId =
                inputData.getString(DISPATCH_ID_PARAM)
                    ?: throw SDKError.InternalError("Dispatch identifier missing, the parameter is required.")
            val customUrl = inputData.getString(CUSTOM_URL_PARAM)
            val requestTimeout = inputData.getLong(REQUEST_TIMEOUT_PARAM, Configuration.DEFAULT_REQUEST_TIMEOUT)
            val configuration =
                Configuration(
                    context = applicationContext,
                    sdkKey = sdkKey,
                    endpoint = customUrl?.let { Endpoint.Custom(it) } ?: Endpoint.Default,
                    requestTimeout = requestTimeout,
                )
            this.applicationContext.dispatchSignals(configuration, dispatchId)
            Log.d("DispatchSignalsWorker", "Dispatching signals, returning success. ${this.id}")
            Result.success()
        } catch (error: Throwable) {
            Log.e("DispatchSignalsWorker", "Errors: ${error.message}")
            val output =
                workDataOf(
                    SDK_ERROR_MESSAGE_PARAM to error.message,
                )
            Result.failure(output)
        }

    companion object {
        private const val SDK_KEY_PARAM = "sdk_key"
        private const val DISPATCH_ID_PARAM = "dispatch_id"
        private const val CUSTOM_URL_PARAM = "custom_url"
        private const val REQUEST_TIMEOUT_PARAM = "request_timeout"
        const val SDK_ERROR_MESSAGE_PARAM = "sdk_error_message"

        internal fun buildRequest(
            configuration: Configuration,
            dispatchId: String,
        ): WorkRequest {
            val params =
                workDataOf(
                    SDK_KEY_PARAM to configuration.sdkKey,
                    DISPATCH_ID_PARAM to dispatchId,
                    CUSTOM_URL_PARAM to configuration.endpointAddress,
                    REQUEST_TIMEOUT_PARAM to configuration.requestTimeout,
                )
            return OneTimeWorkRequestBuilder<DispatchSignalsWorker>()
                .setInputData(params)
                .build()
        }
    }
}

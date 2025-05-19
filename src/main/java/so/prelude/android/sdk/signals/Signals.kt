package so.prelude.android.sdk.signals

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import so.prelude.android.sdk.Application
import so.prelude.android.sdk.Configuration
import so.prelude.android.sdk.Device
import so.prelude.android.sdk.Hardware
import so.prelude.android.sdk.Network
import so.prelude.android.sdk.SDKError
import so.prelude.android.sdk.Signals
import so.prelude.android.sdk.generatePayload
import so.prelude.android.sdk.network.getCellular
import so.prelude.android.sdk.network.getLan
import so.prelude.android.sdk.request.NetworkResponse
import so.prelude.android.sdk.request.Request
import so.prelude.android.sdk.request.commonHeaders
import so.prelude.android.sdk.signals.families.collect
import so.prelude.android.sdk.support.getDigest
import so.prelude.android.sdk.support.sdkInternalScope
import so.prelude.android.sdk.support.toHexString
import java.net.URL
import java.time.Instant

internal suspend fun Context.dispatchSignals(
    configuration: Configuration,
    dispatchId: String,
    signalsScope: SignalsScope,
): String {
    val signalsUrl: URL =
        try {
            configuration.endpointAddress.toSignalsUrl()
        } catch (e: Throwable) {
            throw SDKError.ConfigurationError("cannot parse dispatch URL: ${e.message}")
        }

    val lanNetwork = getLan()
    val cellularNetwork = getCellular()

    if (lanNetwork == null && cellularNetwork == null) {
        throw SDKError.SystemError("No valid network transports available. A LAN or cellular network is required.")
    }

    val signals = Signals.collect(this, dispatchId)
    val payload = generatePayload(signals, getSignaturesList().firstOrNull())

    val results =
        buildNetworkJobs(
            scope = sdkInternalScope,
            signalsUrl = signalsUrl,
            configuration = configuration,
            lanNetwork = lanNetwork,
            cellularNetwork = cellularNetwork,
            signalsId = signals.id,
            payload = payload,
            signalsScope = signalsScope,
        ).awaitAll()

    if (results.any { it is NetworkResponse.Error }) {
        throw SDKError.RequestError("one or more requests failed to execute")
    }
    return signals.id
}

private fun Signals.Companion.collect(
    context: Context,
    dispatchId: String,
): Signals =
    with(context.applicationContext) {
        Signals(
            dispatchId,
            Instant.now(),
            Application.collect(this),
            Device.collect(this),
            Hardware.collect(this),
            Network.collect(this),
        )
    }

private fun buildNetworkJobs(
    scope: CoroutineScope,
    signalsUrl: URL,
    configuration: Configuration,
    lanNetwork: android.net.Network?,
    cellularNetwork: android.net.Network?,
    signalsId: String,
    payload: ByteArray,
    signalsScope: SignalsScope,
): List<Deferred<NetworkResponse>> {
    val jobs = mutableListOf<Deferred<NetworkResponse>>()
    val sdkHeaders =
        mapOf(
            "X-SDK-DispatchID" to signalsId,
            "X-SDK-Key" to configuration.sdkKey,
        )

    when {
        lanNetwork != null && cellularNetwork != null -> {
            jobs.add(
                cellularNetwork.requestJob(
                    scope = scope,
                    signalsUrl = signalsUrl,
                    sdkHeaders = sdkHeaders,
                    requestTimeout = configuration.requestTimeout,
                    maxRetries = configuration.maxRetries,
                ),
            )
            if (signalsScope == SignalsScope.FULL) {
                jobs.add(
                    lanNetwork.requestJob(
                        scope = scope,
                        signalsUrl = signalsUrl,
                        sdkHeaders = sdkHeaders,
                        requestTimeout = configuration.requestTimeout,
                        maxRetries = configuration.maxRetries,
                        payload = payload,
                    ),
                )
            }
        }
        lanNetwork != null -> {
            jobs.add(
                lanNetwork.requestJob(
                    scope = scope,
                    signalsUrl = signalsUrl,
                    sdkHeaders = sdkHeaders,
                    requestTimeout = configuration.requestTimeout,
                    maxRetries = configuration.maxRetries,
                    payload = if (signalsScope == SignalsScope.FULL) payload else null,
                ),
            )
        }
        cellularNetwork != null -> {
            jobs.add(
                cellularNetwork.requestJob(
                    scope = scope,
                    signalsUrl = signalsUrl,
                    sdkHeaders = sdkHeaders,
                    requestTimeout = configuration.requestTimeout,
                    maxRetries = configuration.maxRetries,
                    payload = if (signalsScope == SignalsScope.FULL) payload else null,
                ),
            )
        }
        else -> Unit
    }

    return jobs
}

private fun android.net.Network.requestJob(
    scope: CoroutineScope,
    signalsUrl: URL,
    sdkHeaders: Map<String, String>,
    requestTimeout: Long,
    maxRetries: Int,
    payload: ByteArray? = null,
): Deferred<NetworkResponse> =
    scope.async {
        val contentHeaders =
            mapOf(
                "Content-Encoding" to "deflate",
                "Content-Type" to "application/vnd.prelude.signals",
            )
        Request(
            url = signalsUrl,
            method = if (payload != null) "POST" else "OPTIONS",
            headers = commonHeaders + sdkHeaders + contentHeaders,
            timeout = requestTimeout,
            maxRetries = maxRetries,
            body = payload,
        ).send(this@requestJob)
    }

private fun String.toSignalsUrl(): URL = URL("$this/v1/signals")

@Suppress("DEPRECATION")
private fun Context.getSignaturesList(digestAlgorithm: String = "SHA-256"): List<String> {
    val signatureList: List<String>
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val sig = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            signatureList =
                if (sig.hasMultipleSigners()) {
                    sig.apkContentsSigners.map {
                        it.toByteArray().getDigest(digestAlgorithm).toHexString()
                    }
                } else {
                    sig.signingCertificateHistory.map {
                        it.toByteArray().getDigest(digestAlgorithm).toHexString()
                    }
                }
        } else {
            val sig = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            signatureList =
                sig.map {
                    it.toByteArray().getDigest(digestAlgorithm).toHexString()
                }
        }
        return signatureList
    } catch (e: Exception) {
        Log.e("Signals", "Error reading the package signature: ${e.message}")
    }
    return emptyList()
}

package so.prelude.android.sdk.signals.families

import android.Manifest.permission.READ_BASIC_PHONE_STATE
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.DATA_CONNECTED
import android.telephony.TelephonyManager.SIM_STATE_READY
import androidx.core.content.ContextCompat
import so.prelude.android.sdk.Network

internal fun Network.Companion.collect(context: Context): Network {
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    val cellularData: Boolean? by lazy {
        telephonyManager.simState == SIM_STATE_READY &&
            telephonyManager.dataState == DATA_CONNECTED
    }

    val vpnEnabled: Boolean? by lazy {
        val cm = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager?
        val activeNetwork: android.net.Network? = cm?.activeNetwork
        val caps: NetworkCapabilities? = cm?.getNetworkCapabilities(activeNetwork)
        val vpnInUse = caps?.hasTransport(TRANSPORT_VPN)
        return@lazy vpnInUse
    }

    @SuppressWarnings("MissingPermission")
    val cellularTechnologies: List<String>? by lazy {
        if (ContextCompat.checkSelfPermission(
                context,
                READ_BASIC_PHONE_STATE,
            ) == PERMISSION_GRANTED
        ) {
            listOf(
                telephonyManager.dataNetworkType.toString(),
            )
        } else {
            null
        }
    }

    return Network(
        cellularData,
        cellularTechnologies,
        vpnEnabled,
    )
}

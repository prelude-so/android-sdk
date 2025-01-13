package so.prelude.android.sdk.signals.families

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import so.prelude.android.sdk.Network

internal fun Network.Companion.collect(context: Context): Network {
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    val cellularData: Boolean? by lazy {
        telephonyManager.simState == TelephonyManager.SIM_STATE_READY &&
            telephonyManager.dataState == TelephonyManager.DATA_CONNECTED
    }

    @SuppressWarnings("MissingPermission")
    val cellularTechnologies: List<String>? by lazy {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_BASIC_PHONE_STATE,
            ) == PackageManager.PERMISSION_GRANTED
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
    )
}

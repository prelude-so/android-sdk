package so.prelude.android.sdk.support

import java.security.MessageDigest

internal fun ByteArray.toHexString(humanReadable: Boolean = true): String {
    val hexArray = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
    val hexChars = CharArray(size * 2)
    var v: Int
    for (j in indices) {
        v = this[j].toInt() and 0xFF
        hexChars[j * 2] = hexArray[v.ushr(4)]
        hexChars[j * 2 + 1] = hexArray[v and 0x0F]
    }
    val result = String(hexChars)
    return if (humanReadable) {
        result.chunked(2).joinToString(":")
    } else {
        result
    }
}

internal fun ByteArray.getDigest(algorithm: String): ByteArray =
    MessageDigest
        .getInstance(algorithm)
        .apply { update(this@getDigest) }
        .digest()

package com.waltermelon.vibedict.data

object HexUtils {
    // Converts a hexadecimal string into a byte array
    fun hexStringToByteArray(s: String): ByteArray {
        // Ensure we only process an even number of characters
        val len = if (s.length % 2 != 0) s.length - 1 else s.length

        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) +
                    Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
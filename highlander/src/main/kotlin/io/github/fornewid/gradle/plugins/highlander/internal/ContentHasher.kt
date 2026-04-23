package io.github.fornewid.gradle.plugins.highlander.internal

import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

internal object ContentHasher {

    private const val BUFFER_SIZE = 8 * 1024

    /**
     * SHA-256 digest of a file, streamed in fixed-size chunks. Returned as a
     * lowercase hex string so the value is cheap to compare and hash.
     */
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            DigestInputStream(input, digest).use { stream ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (stream.read(buffer) != -1) {
                    // DigestInputStream updates the digest via read().
                }
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            sb.append(HEX[(b.toInt() ushr 4) and 0x0F])
            sb.append(HEX[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}

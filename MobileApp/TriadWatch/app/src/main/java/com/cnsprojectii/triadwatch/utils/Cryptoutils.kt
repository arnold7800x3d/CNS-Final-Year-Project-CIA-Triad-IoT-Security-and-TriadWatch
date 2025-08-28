package com.cnsprojectii.triadwatch.utils

import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val AES_ALGORITHM = "AES/CBC/PKCS7Padding"
    private const val HASH_ALGORITHM = "SHA-256"
    private const val IV_LENGTH = 16 // AES block size for CBC

    fun getDecryptionKey(): SecretKey {
        // Replace with your actual key bytes derived from the ESP32 key
        val keyInts = intArrayOf(
            21, 42, 63, 84, 105, 126, 147, 168,
            189, 210, 231, 252, 17, 34, 51, 68,
            85, 102, 119, 136, 153, 170, 187, 204,
            221, 238, 255, 1, 18, 35, 52, 69
        )
        val keyBytes = ByteArray(keyInts.size) { i -> keyInts[i].toByte() }
        return SecretKeySpec(keyBytes, "AES")
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun decrypt(encryptedBase64DataWithIv: String, key: SecretKey): String? {
        return try {
            val decodedData = Base64.decode(
                encryptedBase64DataWithIv,
                Base64.DEFAULT// This is the problematic part
            )
            if (decodedData.size < 16) {
                Log.e("CryptoUtils", "Decoded data too short for IV.")
                return null
            }
            val iv = decodedData.copyOfRange(0, 16)
            val encryptedData = decodedData.copyOfRange(16, decodedData.size)

            Log.d("CryptoUtils", "IV: ${bytesToHex(iv)}")
            Log.d("CryptoUtils", "Ciphertext: ${bytesToHex(encryptedData)}")

            val cipher = Cipher.getInstance(AES_ALGORITHM)
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
            val decryptedBytes = cipher.doFinal(encryptedData)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e("CryptoUtils", "Decryption failed for data: $encryptedBase64DataWithIv", e)
            null
        }
    }

    fun verifyHash(data: String, expectedHashHex: String): Boolean {
        return try {
            val calculatedHashBytes = MessageDigest.getInstance(HASH_ALGORITHM)
                .digest(data.toByteArray(StandardCharsets.UTF_8))
            val calculatedHashHex = bytesToHex(calculatedHashBytes)
            val result = calculatedHashHex.equals(expectedHashHex, ignoreCase = true)
            if (!result) {
                Log.w(
                    "CryptoUtils",
                    "Hash mismatch! Data: \"$data\", Calculated: $calculatedHashHex, Expected: $expectedHashHex"
                )
            }
            result
        } catch (e: Exception) {
            Log.e("CryptoUtils", "Hash verification failed", e)
            false
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = "0123456789abcdef"[v ushr 4]
            hexChars[j * 2 + 1] = "0123456789abcdef"[v and 0x0F]
        }
        return String(hexChars)
    }
}
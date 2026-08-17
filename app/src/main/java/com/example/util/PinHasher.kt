package com.example.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for the fallback app-lock PIN.
 *
 * The previous build stored the PIN as cleartext in SharedPreferences and, worse, accepted the
 * hardcoded values "1234" and "0000" on every device regardless of what the user had set - a
 * universal backdoor past the lock screen. Both are gone.
 *
 * A 4-6 digit PIN has a tiny keyspace, so this cannot be made genuinely brute-force resistant
 * against someone with a rooted device and a copy of the prefs file. The iteration count raises
 * the cost of an offline guess, and the app lock is a convenience layer over Android's own
 * screen lock rather than a replacement for it.
 */
object PinHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(pin: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Constant-time compare so a wrong PIN cannot be narrowed down by timing. */
    fun verify(pin: String, saltBase64: String, expectedHashBase64: String): Boolean {
        if (saltBase64.isBlank() || expectedHashBase64.isBlank()) return false
        return try {
            val actual = hash(pin, saltBase64).toByteArray(Charsets.UTF_8)
            val expected = expectedHashBase64.toByteArray(Charsets.UTF_8)
            MessageDigest.isEqual(actual, expected)
        } catch (e: Exception) {
            false
        }
    }
}

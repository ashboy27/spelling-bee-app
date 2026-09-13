package com.spellinggate.app.security

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object BypassPasswordVerifier {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_HEX = "9f3c2a7e4b8d1065c1f0a927dd43be71"
    private const val EXPECTED_HASH_HEX =
        "1b38dec819bd71aa751d1ad909b6e00bacfe6f8c4cb73a143da56a109a41aac5"

    private val salt = SALT_HEX.hexToBytes()
    private val expectedHash = EXPECTED_HASH_HEX.hexToBytes()

    fun verify(candidate: String): Boolean {
        if (candidate.isEmpty()) return false

        val passwordCharacters = candidate.toCharArray()
        val keySpec = PBEKeySpec(
            passwordCharacters,
            salt,
            ITERATIONS,
            KEY_LENGTH_BITS,
        )

        return try {
            val candidateHash = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec)
                .encoded
            try {
                MessageDigest.isEqual(candidateHash, expectedHash)
            } finally {
                candidateHash.fill(0)
            }
        } finally {
            keySpec.clearPassword()
            passwordCharacters.fill('\u0000')
        }
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex text must contain an even number of characters." }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}

package com.example.myapplication.local.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val ALGORITMO = "PBKDF2WithHmacSHA256"
    private const val PREFIJO = "pbkdf2_sha256"
    private const val ITERACIONES = 120_000
    private const val LONGITUD_HASH_BITS = 256
    private const val LONGITUD_SALT_BYTES = 16

    fun generarHash(password: String): String {
        val salt = ByteArray(LONGITUD_SALT_BYTES)
        SecureRandom().nextBytes(salt)

        val hash = generarHashInterno(
            password = password,
            salt = salt,
            iteraciones = ITERACIONES
        )

        return listOf(
            PREFIJO,
            ITERACIONES.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(hash, Base64.NO_WRAP)
        ).joinToString("$")
    }

    fun verificarPassword(passwordIngresado: String, passwordGuardado: String): Boolean {
        if (!esHashValido(passwordGuardado)) {
            // Compatibilidad temporal con usuarios viejos guardados en texto plano.
            return passwordIngresado.trim() == passwordGuardado.trim()
        }

        return try {
            val partes = passwordGuardado.split("$")
            val iteraciones = partes[1].toInt()
            val salt = Base64.decode(partes[2], Base64.NO_WRAP)
            val hashGuardado = Base64.decode(partes[3], Base64.NO_WRAP)

            val hashIngresado = generarHashInterno(
                password = passwordIngresado,
                salt = salt,
                iteraciones = iteraciones
            )

            MessageDigest.isEqual(hashGuardado, hashIngresado)
        } catch (e: Exception) {
            false
        }
    }

    fun necesitaRehash(passwordGuardado: String): Boolean {
        return !esHashValido(passwordGuardado)
    }

    private fun esHashValido(passwordGuardado: String): Boolean {
        val partes = passwordGuardado.split("$")
        return partes.size == 4 && partes[0] == PREFIJO
    }

    private fun generarHashInterno(
        password: String,
        salt: ByteArray,
        iteraciones: Int
    ): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            iteraciones,
            LONGITUD_HASH_BITS
        )

        return try {
            SecretKeyFactory.getInstance(ALGORITMO)
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }
}
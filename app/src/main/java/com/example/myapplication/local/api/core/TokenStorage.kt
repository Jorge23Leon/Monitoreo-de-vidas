package com.example.myapplication.local.api.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CredencialesRecordadas(
    val username: String,
    val password: String
)

class TokenStorage(
    context: Context
) {
    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(
        "tokens_api",
        Context.MODE_PRIVATE
    )

    /*
     * Se usan preferencias separadas para no mezclar tokens de sesión
     * con las credenciales que el usuario decidió recordar.
     */
    private val prefsCredenciales = appContext.getSharedPreferences(
        "credenciales_recordadas_api",
        Context.MODE_PRIVATE
    )

    fun guardarTokens(
        access: String?,
        refresh: String?
    ) {
        prefs.edit()
            .putString("access_token", access)
            .putString("refresh_token", refresh)
            .apply()
    }

    fun guardarAccessToken(access: String) {
        prefs.edit()
            .putString("access_token", access)
            .apply()
    }

    fun obtenerAccessToken(): String? {
        return prefs.getString("access_token", null)
    }

    fun obtenerRefreshToken(): String? {
        return prefs.getString("refresh_token", null)
    }

    fun limpiarTokens() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .apply()
    }

    /*
     * Guarda usuario y contraseña solo cuando la persona marcó
     * “Recordar usuario y contraseña”.
     *
     * Ambos valores se cifran con una clave AES/GCM alojada en Android Keystore.
     * Nunca se guarda la contraseña como texto legible en SharedPreferences.
     */
    fun guardarCredencialesRecordadas(
        username: String,
        password: String
    ): Boolean {
        val usernameLimpio = username.trim()

        if (usernameLimpio.isBlank() || password.isBlank()) {
            limpiarCredencialesRecordadas()
            return false
        }

        return try {
            val usernameCifrado = cifrar(usernameLimpio)
            val passwordCifrada = cifrar(password)

            prefsCredenciales.edit()
                .putBoolean(CLAVE_RECORDAR_ACTIVO, true)
                .putString(CLAVE_USERNAME_CIFRADO, usernameCifrado.datosCifrados)
                .putString(CLAVE_USERNAME_IV, usernameCifrado.iv)
                .putString(CLAVE_PASSWORD_CIFRADA, passwordCifrada.datosCifrados)
                .putString(CLAVE_PASSWORD_IV, passwordCifrada.iv)
                .apply()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            limpiarCredencialesRecordadas()
            false
        }
    }

    fun obtenerCredencialesRecordadas(): CredencialesRecordadas? {
        if (!prefsCredenciales.getBoolean(CLAVE_RECORDAR_ACTIVO, false)) {
            return null
        }

        val usernameCifrado = prefsCredenciales.getString(
            CLAVE_USERNAME_CIFRADO,
            null
        )
        val usernameIv = prefsCredenciales.getString(
            CLAVE_USERNAME_IV,
            null
        )
        val passwordCifrada = prefsCredenciales.getString(
            CLAVE_PASSWORD_CIFRADA,
            null
        )
        val passwordIv = prefsCredenciales.getString(
            CLAVE_PASSWORD_IV,
            null
        )

        if (
            usernameCifrado.isNullOrBlank() ||
            usernameIv.isNullOrBlank() ||
            passwordCifrada.isNullOrBlank() ||
            passwordIv.isNullOrBlank()
        ) {
            limpiarCredencialesRecordadas()
            return null
        }

        return try {
            val username = descifrar(
                datosCifrados = usernameCifrado,
                iv = usernameIv
            )
            val password = descifrar(
                datosCifrados = passwordCifrada,
                iv = passwordIv
            )

            if (username.isBlank() || password.isBlank()) {
                limpiarCredencialesRecordadas()
                null
            } else {
                CredencialesRecordadas(
                    username = username,
                    password = password
                )
            }
        } catch (e: Exception) {
            /*
             * Puede ocurrir si se restauró una copia de app en otro teléfono:
             * la clave del Keystore no existe en ese dispositivo.
             */
            e.printStackTrace()
            limpiarCredencialesRecordadas()
            null
        }
    }

    fun limpiarCredencialesRecordadas() {
        prefsCredenciales.edit()
            .clear()
            .apply()
    }

    /*
     * Se utiliza cuando se cambió la contraseña desde Perfil.
     * Solo cambia la contraseña recordada si corresponde al mismo usuario.
     */
    fun actualizarPasswordRecordada(
        username: String,
        nuevaPassword: String
    ): Boolean {
        val credenciales = obtenerCredencialesRecordadas() ?: return false

        if (credenciales.username != username.trim()) {
            return false
        }

        return guardarCredencialesRecordadas(
            username = credenciales.username,
            password = nuevaPassword
        )
    }

    private fun cifrar(valor: String): ValorCifrado {
        val cipher = Cipher.getInstance(TRANSFORMACION_AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, obtenerOCrearClave())

        val datosCifrados = cipher.doFinal(
            valor.toByteArray(StandardCharsets.UTF_8)
        )

        return ValorCifrado(
            datosCifrados = Base64.encodeToString(
                datosCifrados,
                Base64.NO_WRAP
            ),
            iv = Base64.encodeToString(
                cipher.iv,
                Base64.NO_WRAP
            )
        )
    }

    private fun descifrar(
        datosCifrados: String,
        iv: String
    ): String {
        val cipher = Cipher.getInstance(TRANSFORMACION_AES_GCM)
        val ivBytes = Base64.decode(iv, Base64.NO_WRAP)

        cipher.init(
            Cipher.DECRYPT_MODE,
            obtenerOCrearClave(),
            GCMParameterSpec(128, ivBytes)
        )

        val resultado = cipher.doFinal(
            Base64.decode(datosCifrados, Base64.NO_WRAP)
        )

        return String(resultado, StandardCharsets.UTF_8)
    }

    private fun obtenerOCrearClave(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        val claveExistente = keyStore.getKey(
            ALIAS_CLAVE_CREDENCIALES,
            null
        ) as? SecretKey

        if (claveExistente != null) {
            return claveExistente
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val especificacion = KeyGenParameterSpec.Builder(
            ALIAS_CLAVE_CREDENCIALES,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        keyGenerator.init(especificacion)
        return keyGenerator.generateKey()
    }

    private data class ValorCifrado(
        val datosCifrados: String,
        val iv: String
    )

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS_CLAVE_CREDENCIALES =
            "com.example.myapplication.local.clave_credenciales_recordadas"

        const val TRANSFORMACION_AES_GCM = "AES/GCM/NoPadding"

        const val CLAVE_RECORDAR_ACTIVO = "recordar_credenciales_activo"
        const val CLAVE_USERNAME_CIFRADO = "username_cifrado"
        const val CLAVE_USERNAME_IV = "username_iv"
        const val CLAVE_PASSWORD_CIFRADA = "password_cifrada"
        const val CLAVE_PASSWORD_IV = "password_iv"
    }
}

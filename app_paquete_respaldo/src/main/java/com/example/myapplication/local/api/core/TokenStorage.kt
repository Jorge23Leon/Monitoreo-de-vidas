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

/**
 * Modelo simple para regresar credenciales recordadas.
 */
data class CredencialesRecordadas(
    val username: String,
    val password: String
)

/**
 * Esta clase maneja el almacenamiento local
 */
class TokenStorage(

    // Context se recibe para acceder a SharedPreferences y Android Keystore.
    context: Context

) {

    // Se guarda applicationContext para evitar fugas de memoria.
    private val appContext = context.applicationContext

    /**
     * SharedPreferences para tokens de API.
     *
     * Aquí se guardan:
     * - access_token
     * - refresh_token
     *
     * Nombre del archivo interno:
     * tokens_api
     */
    private val prefs = appContext.getSharedPreferences(
        "tokens_api",
        Context.MODE_PRIVATE
    )

    /*
     * SharedPreferences separadas para credenciales recordadas.
     */
    private val prefsCredenciales = appContext.getSharedPreferences(
        "credenciales_recordadas_api",
        Context.MODE_PRIVATE
    )

    /**
     * Guarda access token y refresh token después de iniciar sesión.
     */
    fun guardarTokens(
        access: String?,
        refresh: String?
    ) {
        try {
            val accessCifrado = access
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(::cifrar)
            val refreshCifrado = refresh
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(::cifrar)

            prefs.edit().apply {
                guardarValorCifrado(
                    claveDatos = CLAVE_ACCESS_CIFRADO,
                    claveIv = CLAVE_ACCESS_IV,
                    valor = accessCifrado
                )
                guardarValorCifrado(
                    claveDatos = CLAVE_REFRESH_CIFRADO,
                    claveIv = CLAVE_REFRESH_IV,
                    valor = refreshCifrado
                )
                remove(CLAVE_ACCESS_LEGACY)
                remove(CLAVE_REFRESH_LEGACY)
            }.apply()
        } catch (error: Exception) {
            throw IllegalStateException("No se pudieron proteger los tokens de sesión.", error)
        }
    }

    /**
     * Guarda únicamente el access token.
     */
    fun guardarAccessToken(access: String) {
        val accessLimpio = access.trim()
        require(accessLimpio.isNotBlank()) { "El access token no puede estar vacío." }

        try {
            val cifrado = cifrar(accessLimpio)
            prefs.edit()
                .putString(CLAVE_ACCESS_CIFRADO, cifrado.datosCifrados)
                .putString(CLAVE_ACCESS_IV, cifrado.iv)
                .remove(CLAVE_ACCESS_LEGACY)
                .apply()
        } catch (error: Exception) {
            throw IllegalStateException("No se pudo proteger el access token.", error)
        }
    }

    /**
     * Obtiene el access token guardado.
     */
    fun obtenerAccessToken(): String? {
        return obtenerTokenProtegido(
            claveDatos = CLAVE_ACCESS_CIFRADO,
            claveIv = CLAVE_ACCESS_IV,
            claveLegacy = CLAVE_ACCESS_LEGACY,
            guardarMigrado = ::guardarAccessToken
        )
    }

    /**
     * Obtiene el refresh token guardado.
     */
    fun obtenerRefreshToken(): String? {
        return obtenerTokenProtegido(
            claveDatos = CLAVE_REFRESH_CIFRADO,
            claveIv = CLAVE_REFRESH_IV,
            claveLegacy = CLAVE_REFRESH_LEGACY,
            guardarMigrado = { valor ->
                val accessActual = obtenerAccessToken()
                guardarTokens(accessActual, valor)
            }
        )
    }

    /**
     * Borra access token y refresh token.
     */
    fun limpiarTokens() {
        prefs.edit()
            .remove(CLAVE_ACCESS_CIFRADO)
            .remove(CLAVE_ACCESS_IV)
            .remove(CLAVE_REFRESH_CIFRADO)
            .remove(CLAVE_REFRESH_IV)
            .remove(CLAVE_ACCESS_LEGACY)
            .remove(CLAVE_REFRESH_LEGACY)
            .apply()
    }

    private fun android.content.SharedPreferences.Editor.guardarValorCifrado(
        claveDatos: String,
        claveIv: String,
        valor: ValorCifrado?
    ) {
        if (valor == null) {
            remove(claveDatos)
            remove(claveIv)
        } else {
            putString(claveDatos, valor.datosCifrados)
            putString(claveIv, valor.iv)
        }
    }

    private fun obtenerTokenProtegido(
        claveDatos: String,
        claveIv: String,
        claveLegacy: String,
        guardarMigrado: (String) -> Unit
    ): String? {
        val datos = prefs.getString(claveDatos, null)
        val iv = prefs.getString(claveIv, null)

        if (!datos.isNullOrBlank() && !iv.isNullOrBlank()) {
            return try {
                descifrar(datos, iv).trim().takeIf(String::isNotBlank)
            } catch (_: Exception) {
                prefs.edit().remove(claveDatos).remove(claveIv).apply()
                null
            }
        }

        // Migra automáticamente instalaciones anteriores que guardaban texto plano.
        val legacy = prefs.getString(claveLegacy, null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null

        return runCatching {
            guardarMigrado(legacy)
            legacy
        }.getOrNull()
    }

    /*
     * Guarda usuario y contraseña solo cuando la persona marcó
     * “Recordar usuario y contraseña”.
     */
    fun guardarCredencialesRecordadas(
        username: String,
        password: String
    ): Boolean {

        // Limpia espacios del usuario.
        val usernameLimpio = username.trim()

        // Si usuario o contraseña vienen vacíos, se borran credenciales guardadas.
        if (usernameLimpio.isBlank() || password.isBlank()) {
            limpiarCredencialesRecordadas()
            return false
        }

        return try {

            // Cifra el username limpio.
            val usernameCifrado = cifrar(usernameLimpio)

            // Cifra la contraseña.
            val passwordCifrada = cifrar(password)

            // Guarda bandera, datos cifrados e IVs.
            prefsCredenciales.edit()

                // Indica que sí hay credenciales recordadas.
                .putBoolean(CLAVE_RECORDAR_ACTIVO, true)

                // Guarda username cifrado.
                .putString(CLAVE_USERNAME_CIFRADO, usernameCifrado.datosCifrados)

                // Guarda IV usado para cifrar username.
                .putString(CLAVE_USERNAME_IV, usernameCifrado.iv)

                // Guarda password cifrada.
                .putString(CLAVE_PASSWORD_CIFRADA, passwordCifrada.datosCifrados)

                // Guarda IV usado para cifrar password.
                .putString(CLAVE_PASSWORD_IV, passwordCifrada.iv)

                // Aplica cambios.
                .apply()

            // Regresa true porque se guardó correctamente.
            true
        } catch (e: Exception) {

            // Imprime error para desarrollo.
            e.printStackTrace()

            // Si algo falla cifrando, limpiamos para no dejar datos corruptos.
            limpiarCredencialesRecordadas()

            // Regresa false porque no se pudieron guardar.
            false
        }
    }

    /**
     * Obtiene usuario y contraseña recordados.
     */
    fun obtenerCredencialesRecordadas(): CredencialesRecordadas? {

        // Si la bandera no está activa, significa que el usuario no pidió recordar.
        if (!prefsCredenciales.getBoolean(CLAVE_RECORDAR_ACTIVO, false)) {
            return null
        }

        // Leemos username cifrado.
        val usernameCifrado = prefsCredenciales.getString(
            CLAVE_USERNAME_CIFRADO,
            null
        )

        // Leemos IV del username.
        val usernameIv = prefsCredenciales.getString(
            CLAVE_USERNAME_IV,
            null
        )

        // Leemos password cifrada.
        val passwordCifrada = prefsCredenciales.getString(
            CLAVE_PASSWORD_CIFRADA,
            null
        )

        // Leemos IV de password.
        val passwordIv = prefsCredenciales.getString(
            CLAVE_PASSWORD_IV,
            null
        )

        // Si falta cualquier pieza, los datos están incompletos.
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

            // Descifra el username usando sus datos cifrados y su IV.
            val username = descifrar(
                datosCifrados = usernameCifrado,
                iv = usernameIv
            )

            // Descifra el password usando sus datos cifrados y su IV.
            val password = descifrar(
                datosCifrados = passwordCifrada,
                iv = passwordIv
            )

            // Si al descifrar algo queda vacío, se limpia por seguridad.
            if (username.isBlank() || password.isBlank()) {
                limpiarCredencialesRecordadas()
                null
            } else {

                // Regresa las credenciales ya descifradas para llenar el login.
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

            // Imprime error para desarrollo.
            e.printStackTrace()

            // Borra datos que ya no se pueden descifrar.
            limpiarCredencialesRecordadas()

            // No hay credenciales válidas.
            null
        }
    }

    /**
     * Borra todas las credenciales recordadas.

     */
    fun limpiarCredencialesRecordadas() {
        prefsCredenciales.edit()

            // clear() borra todo el archivo credenciales_recordadas_api.
            .clear()

            // Aplica cambios.
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

        // Intenta leer credenciales recordadas actuales.
        // Si no existen, no hay nada que actualizar.
        val credenciales = obtenerCredencialesRecordadas() ?: return false

        // Solo actualiza si el username coincide con el usuario guardado.
        if (credenciales.username != username.trim()) {
            return false
        }

        // Vuelve a guardar el mismo usuario con la nueva contraseña cifrada.
        return guardarCredencialesRecordadas(
            username = credenciales.username,
            password = nuevaPassword
        )
    }

    /**
     * Cifra un texto usando AES/GCM.
     */
    private fun cifrar(valor: String): ValorCifrado {

        // Crea un Cipher con AES/GCM/NoPadding.
        val cipher = Cipher.getInstance(TRANSFORMACION_AES_GCM)

        // Inicializa el Cipher en modo cifrado usando la clave segura.
        cipher.init(Cipher.ENCRYPT_MODE, obtenerOCrearClave())

        // Convierte el texto a bytes y lo cifra.
        val datosCifrados = cipher.doFinal(
            valor.toByteArray(StandardCharsets.UTF_8)
        )

        // Regresa datos cifrados e IV en Base64 para poder guardarlos como String.
        return ValorCifrado(

            // Bytes cifrados convertidos a texto Base64.
            datosCifrados = Base64.encodeToString(
                datosCifrados,
                Base64.NO_WRAP
            ),

            // IV generado automáticamente por cipher.init().
            iv = Base64.encodeToString(
                cipher.iv,
                Base64.NO_WRAP
            )
        )
    }

    /**
     * Descifra texto cifrado con AES/GCM.
     */
    private fun descifrar(
        datosCifrados: String,
        iv: String
    ): String {

        // Crea un Cipher con AES/GCM/NoPadding.
        val cipher = Cipher.getInstance(TRANSFORMACION_AES_GCM)

        // Convierte el IV de Base64 a bytes.
        val ivBytes = Base64.decode(iv, Base64.NO_WRAP)

        // Inicializa el Cipher en modo descifrado con la misma clave y el mismo IV.
        cipher.init(
            Cipher.DECRYPT_MODE,
            obtenerOCrearClave(),
            GCMParameterSpec(128, ivBytes)
        )

        // Descifra los datos Base64.
        val resultado = cipher.doFinal(
            Base64.decode(datosCifrados, Base64.NO_WRAP)
        )

        // Convierte bytes descifrados a String UTF-8.
        return String(resultado, StandardCharsets.UTF_8)
    }

    /**
     * Obtiene una clave AES desde Android Keystore.
     */
    private fun obtenerOCrearClave(): SecretKey {

        // Abre Android Keystore.
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        // Intenta obtener una clave existente con nuestro alias.
        val claveExistente = keyStore.getKey(
            ALIAS_CLAVE_CREDENCIALES,
            null
        ) as? SecretKey

        // Si ya existe, la regresa.
        if (claveExistente != null) {
            return claveExistente
        }

        // Si no existe, se crea un generador de claves AES en Android Keystore.
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        // Configuración de la clave.
        val especificacion = KeyGenParameterSpec.Builder(
            ALIAS_CLAVE_CREDENCIALES,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )

            // Tamaño de clave AES de 256 bits.
            .setKeySize(256)

            // Modo GCM recomendado para cifrado autenticado.
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)

            // GCM no usa padding.
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)

            // Construye la especificación.
            .build()

        // Inicializa el generador con la configuración.
        keyGenerator.init(especificacion)

        // Genera y regresa la clave.
        return keyGenerator.generateKey()
    }

    /**
     * Clase interna para representar el resultado de un cifrado.
     */
    private data class ValorCifrado(
        val datosCifrados: String,
        val iv: String
    )

    /**
     * Constantes privadas de esta clase.
     */
    private companion object {

        // Nombre oficial del almacén seguro de claves de Android.
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        // Alias con el que guardamos/buscamos la clave AES.
        const val ALIAS_CLAVE_CREDENCIALES =
            "com.example.myapplication.local.clave_credenciales_recordadas"

        // Transformación de cifrado usada por Cipher.
        const val TRANSFORMACION_AES_GCM = "AES/GCM/NoPadding"

        const val CLAVE_ACCESS_LEGACY = "access_token"
        const val CLAVE_REFRESH_LEGACY = "refresh_token"
        const val CLAVE_ACCESS_CIFRADO = "access_token_cifrado"
        const val CLAVE_ACCESS_IV = "access_token_iv"
        const val CLAVE_REFRESH_CIFRADO = "refresh_token_cifrado"
        const val CLAVE_REFRESH_IV = "refresh_token_iv"

        // Llave para saber si el usuario activó recordar credenciales.
        const val CLAVE_RECORDAR_ACTIVO = "recordar_credenciales_activo"

        // Llave donde se guarda el username cifrado.
        const val CLAVE_USERNAME_CIFRADO = "username_cifrado"

        // Llave donde se guarda el IV del username.
        const val CLAVE_USERNAME_IV = "username_iv"

        // Llave donde se guarda la password cifrada.
        const val CLAVE_PASSWORD_CIFRADA = "password_cifrada"

        // Llave donde se guarda el IV de la password.
        const val CLAVE_PASSWORD_IV = "password_iv"
    }
}

package com.example.myapplication.local.api.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Evita enviar credenciales JWT a dominios ajenos a la API configurada. */
object ApiOrigin {
    private val baseUrl: HttpUrl by lazy { ApiConfig.BASE_URL.toHttpUrl() }

    fun esMismoOrigen(url: HttpUrl): Boolean =
        url.scheme == baseUrl.scheme &&
                url.host == baseUrl.host &&
                url.port == baseUrl.port

    fun esMismoOrigen(url: String): Boolean = runCatching {
        esMismoOrigen(url.toHttpUrl())
    }.getOrDefault(false)
}

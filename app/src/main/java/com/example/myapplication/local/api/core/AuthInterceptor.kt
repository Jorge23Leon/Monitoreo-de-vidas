package com.example.myapplication.local.api.core

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenStorage: TokenStorage
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestOriginal = chain.request()

        val accessToken = tokenStorage.obtenerAccessToken()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val requestNueva = if (accessToken != null) {
            requestOriginal.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            requestOriginal
        }

        return chain.proceed(requestNueva)
    }
}
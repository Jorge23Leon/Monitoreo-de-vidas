package com.example.myapplication.local.api.core

import android.content.Context

class TokenStorage(
    context: Context
) {
    private val prefs = context.getSharedPreferences(
        "tokens_api",
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

    fun obtenerAccessToken(): String? {
        return prefs.getString("access_token", null)
    }

    fun obtenerRefreshToken(): String? {
        return prefs.getString("refresh_token", null)
    }

    fun limpiarTokens() {
        prefs.edit().clear().apply()
    }
}
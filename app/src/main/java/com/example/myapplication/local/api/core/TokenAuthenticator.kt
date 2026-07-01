package com.example.myapplication.local.api.core

import com.example.myapplication.local.api.auth.AuthApiService
import com.example.myapplication.local.api.auth.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenStorage: TokenStorage,
    private val authApiService: AuthApiService
) : Authenticator {

    companion object {
        private val refreshLock = Any()
    }

    override fun authenticate(
        route: Route?,
        response: Response
    ): Request? {
        // Evita ciclos infinitos si el servidor vuelve a responder 401.
        if (cantidadRespuestas(response) >= 2) {
            return null
        }

        val authorization = response.request.header("Authorization")
            ?: return null

        val tokenUsado = authorization
            .removePrefix("Bearer ")
            .trim()

        if (tokenUsado.isBlank()) {
            return null
        }

        synchronized(refreshLock) {
            /*
             * Otra petición pudo haber renovado el token antes.
             * En ese caso, solo reintentamos con el access nuevo.
             */
            val accessActual = tokenStorage.obtenerAccessToken()
                ?.trim()
                .orEmpty()

            if (accessActual.isNotBlank() && accessActual != tokenUsado) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $accessActual")
                    .build()
            }

            val refreshToken = tokenStorage.obtenerRefreshToken()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return null

            val nuevoAccess = try {
                runBlocking {
                    val refreshResponse = authApiService.refresh(
                        RefreshRequest(refresh = refreshToken)
                    )

                    if (refreshResponse.isSuccessful) {
                        refreshResponse.body()
                            ?.access
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            } ?: return null

            tokenStorage.guardarAccessToken(nuevoAccess)

            return response.request.newBuilder()
                .header("Authorization", "Bearer $nuevoAccess")
                .build()
        }
    }

    private fun cantidadRespuestas(response: Response): Int {
        var contador = 1
        var respuestaAnterior = response.priorResponse

        while (respuestaAnterior != null) {
            contador++
            respuestaAnterior = respuestaAnterior.priorResponse
        }

        return contador
    }
}
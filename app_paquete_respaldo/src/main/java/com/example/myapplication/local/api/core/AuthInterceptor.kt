package com.example.myapplication.local.api.core

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(

    /**
     *Guardas y lees el access_token.
     */
    private val tokenStorage: TokenStorage

) : Interceptor {

    /**
     * intercept() se ejecuta automáticamente cada vez que OkHttp va a mandar
     * una petición HTTP usando el cliente autenticado.
     */
    override fun intercept(chain: Interceptor.Chain): Response {

        // Obtenemos la petición original tal como la creó Retrofit.
        // Ejemplo: GET /api/v1/users/me/
        val requestOriginal = chain.request()

        // Buscamos el access token guardado localmente.
        // obtenerAccessToken(): lee "access_token" desde SharedPreferences.
        // trim(): quita espacios sobrantes.
        // takeIf { it.isNotBlank() }: solo conserva el token si no está vacío.
        val accessToken = tokenStorage.obtenerAccessToken()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        // Creamos la petición que realmente se va a mandar.
        val requestNueva = if (
            accessToken != null &&
            ApiOrigin.esMismoOrigen(requestOriginal.url)
        ) {

            // Si sí existe token, se crea una copia de la petición original
            // agregando el header Authorization.
            requestOriginal.newBuilder()

                // Este header es el que Django/DRF usa para identificar
                // al usuario autenticado por JWT.
                .header("Authorization", "Bearer $accessToken")

                // build() termina de construir la nueva petición.
                .build()
        } else {

            // Si no hay token, se manda la petición original sin Authorization.
            requestOriginal
        }

        // Se continúa la ejecución de la petición.
        // Aquí OkHttp ya manda requestNueva al backend.
        return chain.proceed(requestNueva)
    }
}

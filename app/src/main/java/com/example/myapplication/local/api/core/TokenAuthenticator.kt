package com.example.myapplication.local.api.core


import com.example.myapplication.local.api.auth.AuthApiService
import com.example.myapplication.local.api.auth.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**

 * Esta clase se encarga de renovar automáticamente el access token.
 */
class TokenAuthenticator(

    // Permite leer y guardar tokens locales.
    private val tokenStorage: TokenStorage,

    // Servicio público de auth para llamar refresh.
    private val authApiService: AuthApiService

) : Authenticator {

    /**
     * companion object crea miembros compartidos por todas las instancias.
     */
    companion object {

        /**
         * refreshLock evita que varias peticiones renueven el token al mismo tiempo.
         */
        private val refreshLock = Any()
    }

    /**
     * authenticate() se ejecuta automáticamente cuando el backend responde 401.
     */
    override fun authenticate(

        // Ruta de red. No se usa directamente en este caso.
        route: Route?,

        // Respuesta 401 que disparó el Authenticator.
        response: Response

    ): Request? {

        // Evita ciclos infinitos.
        // Si ya se intentó reenviar la petición y volvió a responder 401,
        // detenemos el proceso regresando null.
        if (cantidadRespuestas(response) >= 2) {
            return null
        }

        // Leemos el header Authorization que se usó en la petición fallida.
        // Si no existía Authorization, no hay token que renovar.
        val authorization = response.request.header("Authorization")
            ?: return null

        // Quitamos el texto "Bearer " para quedarnos solo con el token.
        val tokenUsado = authorization
            .removePrefix("Bearer ")
            .trim()

        // Si por alguna razón el token quedó vacío, no se puede renovar.
        if (tokenUsado.isBlank()) {
            return null
        }

        /**
         * Bloque sincronizado.
         *
         * Garantiza que solo una petición a la vez entre a renovar token.
         */
        synchronized(refreshLock) {

            // Revisamos si otra petición ya renovó el token antes.
            val accessActual = tokenStorage.obtenerAccessToken()
                ?.trim()
                .orEmpty()

            // Si hay un access actual y es diferente al token que falló,
            // significa que ya se renovó antes.
            if (accessActual.isNotBlank() && accessActual != tokenUsado) {

                // Reintentamos la petición original usando el access nuevo.
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $accessActual")
                    .build()
            }

            // Obtenemos el refresh token guardado.
            // Si no existe refresh token, ya no se puede renovar sesión.
            val refreshToken = tokenStorage.obtenerRefreshToken()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return null

            // Intentamos pedir un access nuevo al backend.
            val nuevoAccess = try {

                // authenticate() no es suspend, por eso usamos runBlocking.
                runBlocking {

                    // Mandamos el refresh token en el body.
                    val refreshResponse = authApiService.refresh(
                        RefreshRequest(refresh = refreshToken)
                    )

                    // Si el backend respondió bien, intentamos leer el access.
                    if (refreshResponse.isSuccessful) {
                        refreshResponse.body()
                            ?.access
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                    } else {

                        // Si refresh falló, no se puede renovar.
                        null
                    }
                }
            } catch (e: Exception) {

                // Si hubo error de red, JSON, servidor caído, etc., no se renueva.
                null

                // Si nuevoAccess quedó null, OkHttp no reintenta la petición.
            } ?: return null

            // Guardamos el access token nuevo para futuras peticiones.
            tokenStorage.guardarAccessToken(nuevoAccess)

            // Reintentamos la petición original agregando el nuevo token.
            return response.request.newBuilder()
                .header("Authorization", "Bearer $nuevoAccess")
                .build()
        }
    }

    /**
     * Cuenta cuántas respuestas encadenadas tiene una petición.
     *
     * OkHttp guarda priorResponse cuando una petición se reintentó.
     */
    private fun cantidadRespuestas(response: Response): Int {

        // La respuesta actual cuenta como 1.
        var contador = 1

        // priorResponse es la respuesta anterior, si hubo reintento.
        var respuestaAnterior = response.priorResponse

        // Mientras existan respuestas anteriores, las contamos.
        while (respuestaAnterior != null) {
            contador++
            respuestaAnterior = respuestaAnterior.priorResponse
        }

        // Regresa el total de respuestas encadenadas.
        return contador
    }
}

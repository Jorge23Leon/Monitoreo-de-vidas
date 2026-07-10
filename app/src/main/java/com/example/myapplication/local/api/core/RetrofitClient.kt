package com.example.myapplication.local.api.core

import android.content.Context
import com.example.myapplication.local.api.auth.AuthApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object RetrofitClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {

        // BODY muestra todo el contenido de la petición y respuesta.
        level = HttpLoggingInterceptor.Level.BODY
    }


    private val okHttpClientPublico = OkHttpClient.Builder()

        // Agrega logging para ver la petición/respuesta en Logcat.
        .addInterceptor(loggingInterceptor)

        // Tiempo máximo para establecer conexión con el servidor.
        .connectTimeout(30, TimeUnit.SECONDS)

        // Tiempo máximo esperando respuesta del servidor.
        .readTimeout(30, TimeUnit.SECONDS)

        // Tiempo máximo para enviar el body de una petición.
        .writeTimeout(30, TimeUnit.SECONDS)

        // Construye el cliente público.
        .build()

    /**
     * Retrofit público.
     *
     * Usa:
     * - BASE_URL de ApiConfig.
     * - okHttpClientPublico.
     * - Gson para convertir JSON.
     */
    private val retrofitPublico = Retrofit.Builder()

        // Dirección base del backend.
        .baseUrl(ApiConfig.BASE_URL)

        // Cliente HTTP público sin token.
        .client(okHttpClientPublico)

        // Convertidor JSON <-> Kotlin data classes.
        .addConverterFactory(GsonConverterFactory.create())

        // Construye la instancia Retrofit.
        .build()

    /**
     * Servicio público de autenticación.
     *
     * by lazy significa que se crea hasta que alguien lo usa por primera vez.
     *
     * Este servicio se usa para login/refresh/signup sin requerir token.
     */
    val authApiService: AuthApiService by lazy {

        // Retrofit crea una implementación real de AuthApiService.
        retrofitPublico.create(AuthApiService::class.java)
    }

    /**
     * Crea un servicio autenticado para cualquier ApiService.
     */
    fun <T> crearServicioAutenticado(

        // Context se usa para crear TokenStorage.
        context: Context,

        // Clase del servicio que se quiere crear.
        serviceClass: Class<T>

    ): T {

        // TokenStorage permite leer access_token y refresh_token.

        val tokenStorage = TokenStorage(context.applicationContext)

        /**
         * Cliente privado/autenticado.
         *
         * Este cliente sí agrega:
         * - AuthInterceptor: mete Authorization: Bearer <access_token>.
         * - TokenAuthenticator: renueva access token cuando recibe 401.
         */
        val okHttpClientPrivado = OkHttpClient.Builder()

            // Agrega el token a cada petición protegida.
            .addInterceptor(AuthInterceptor(tokenStorage))

            // Intenta renovar el token si el backend responde 401.
            .authenticator(
                TokenAuthenticator(
                    tokenStorage = tokenStorage,
                    authApiService = authApiService
                )
            )

            // Muestra logs HTTP en Logcat.
            .addInterceptor(loggingInterceptor)

            // Tiempo máximo para conectar.
            .connectTimeout(30, TimeUnit.SECONDS)

            // Tiempo máximo para leer respuesta.
            .readTimeout(30, TimeUnit.SECONDS)

            // Tiempo máximo para escribir petición.
            .writeTimeout(30, TimeUnit.SECONDS)

            // Construye el cliente privado.
            .build()

        /**
         * Retrofit privado/autenticado.
         *
         * Usa el mismo BASE_URL, pero con un OkHttpClient que sí maneja tokens.
         */
        val retrofitPrivado = Retrofit.Builder()

            // URL base del backend.
            .baseUrl(ApiConfig.BASE_URL)

            // Cliente HTTP autenticado.
            .client(okHttpClientPrivado)

            // Convertidor JSON.
            .addConverterFactory(GsonConverterFactory.create())

            // Construye Retrofit.
            .build()

        // Crea y regresa el servicio solicitado.
        return retrofitPrivado.create(serviceClass)
    }
}

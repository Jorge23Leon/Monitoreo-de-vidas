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
        // BODY serializaba nuevamente respuestas grandes y hacia mas lenta la sync.
        level = HttpLoggingInterceptor.Level.BASIC
    }

    @Volatile
    private var clienteAutenticadoCompartido: OkHttpClient? = null

    @Volatile
    private var clienteImagenesCompartido: OkHttpClient? = null

    private val okHttpClientPublico: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofitPublico: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(okHttpClientPublico)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Servicio público utilizado para login y renovación del access token.
     */
    val authApiService: AuthApiService by lazy {
        retrofitPublico.create(AuthApiService::class.java)
    }

    /**
     * Crea el mismo cliente autenticado para Retrofit y para descargas directas.
     *
     * Esto permite que las fotografías:
     * - reciban Authorization: Bearer <token>;
     * - renueven automáticamente el access token cuando el servidor responda 401;
     * - sigan redirecciones y compartan los mismos tiempos de espera.
     */
    fun crearClienteAutenticado(context: Context): OkHttpClient {
        clienteAutenticadoCompartido?.let { return it }

        return synchronized(this) {
            clienteAutenticadoCompartido ?: run {
                val tokenStorage = TokenStorage(context.applicationContext)

                OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor(tokenStorage))
                    .authenticator(
                        TokenAuthenticator(
                            tokenStorage = tokenStorage,
                            authApiService = authApiService
                        )
                    )
                    .addInterceptor(loggingInterceptor)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .callTimeout(60, TimeUnit.SECONDS)
                    .build()
                    .also { clienteAutenticadoCompartido = it }
            }
        }
    }

    /**
     * Cliente exclusivo para imágenes de catálogo.
     *
     * No usa BODY logging para evitar procesar archivos binarios completos en Logcat
     * y limita el tiempo de cada URL para que una imagen rota no bloquee la sincronización.
     */
    fun crearClienteImagenesAutenticado(context: Context): OkHttpClient {
        clienteImagenesCompartido?.let { return it }

        return synchronized(this) {
            clienteImagenesCompartido ?: run {
                val tokenStorage = TokenStorage(context.applicationContext)

                OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor(tokenStorage))
                    .authenticator(
                        TokenAuthenticator(
                            tokenStorage = tokenStorage,
                            authApiService = authApiService
                        )
                    )
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .callTimeout(20, TimeUnit.SECONDS)
                    .build()
                    .also { clienteImagenesCompartido = it }
            }
        }
    }

    /**
     * Crea un servicio Retrofit protegido por JWT.
     */
    fun <T> crearServicioAutenticado(
        context: Context,
        serviceClass: Class<T>
    ): T {
        val retrofitPrivado = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(crearClienteAutenticado(context.applicationContext))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofitPrivado.create(serviceClass)
    }
}

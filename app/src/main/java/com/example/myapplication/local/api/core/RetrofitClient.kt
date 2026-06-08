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
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClientPublico = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofitPublico = Retrofit.Builder()
        .baseUrl(ApiConfig.BASE_URL)
        .client(okHttpClientPublico)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApiService: AuthApiService by lazy {
        retrofitPublico.create(AuthApiService::class.java)
    }

    fun <T> crearServicioAutenticado(
        context: Context,
        serviceClass: Class<T>
    ): T {
        val tokenStorage = TokenStorage(context)

        val okHttpClientPrivado = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStorage))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofitPrivado = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(okHttpClientPrivado)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofitPrivado.create(serviceClass)
    }
}
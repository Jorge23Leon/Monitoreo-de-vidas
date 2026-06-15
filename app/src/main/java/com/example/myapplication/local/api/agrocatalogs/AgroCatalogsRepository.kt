package com.example.myapplication.local.api.agrocatalogs

import android.content.Context
import com.example.myapplication.local.api.core.RetrofitClient

class AgroCatalogsRepository(
    context: Context
) {
    private val api: AgroCatalogsApiService =
        RetrofitClient.crearServicioAutenticado(
            context = context,
            serviceClass = AgroCatalogsApiService::class.java
        )

    suspend fun obtenerTodosLosCultivos(): ResultadoAgroCatalogsApi {
        return try {
            val todos = mutableListOf<AgroCropApiItem>()
            var page = 1

            while (true) {
                val response = api.listarCultivos(page = page)

                if (!response.isSuccessful) {
                    val error = response.errorBody()?.string()
                    return ResultadoAgroCatalogsApi.Error(
                        "Error cultivos: ${response.code()} ${error ?: response.message()}"
                    )
                }

                val body = response.body()
                    ?: return ResultadoAgroCatalogsApi.Error("El servidor respondió vacío en cultivos")

                todos.addAll(body.results)

                if (body.next.isNullOrBlank()) {
                    break
                }

                page++
            }

            ResultadoAgroCatalogsApi.Exito(todos)
        } catch (e: Exception) {
            ResultadoAgroCatalogsApi.Error(
                "No se pudieron cargar cultivos: ${e.message}"
            )
        }
    }
}

sealed class ResultadoAgroCatalogsApi {
    data class Exito(
        val cultivos: List<AgroCropApiItem>
    ) : ResultadoAgroCatalogsApi()

    data class Error(
        val mensaje: String
    ) : ResultadoAgroCatalogsApi()
}
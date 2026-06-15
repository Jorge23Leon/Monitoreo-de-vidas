package com.example.myapplication.local.api.fieldops

import android.content.Context
import com.example.myapplication.local.api.core.RetrofitClient

class FieldOpsRepository(
    context: Context
) {
    private val api: FieldOpsApiService =
        RetrofitClient.crearServicioAutenticado(
            context = context,
            serviceClass = FieldOpsApiService::class.java
        )

    suspend fun obtenerTodosLosProgramasCampo(
        datacentral: String? = null,
        masterProgram: String? = null,
        plot: String? = null,
        status: String? = null
    ): ResultadoFieldOpsApi {
        return try {
            val todos = mutableListOf<FieldTaskApiItem>()
            var page = 1

            while (true) {
                val response = api.listarProgramasCampo(
                    datacentral = datacentral,
                    masterProgram = masterProgram,
                    plot = plot,
                    status = status,
                    page = page
                )

                if (!response.isSuccessful) {
                    val error = response.errorBody()?.string()
                    return ResultadoFieldOpsApi.Error(
                        "Error programas campo: ${response.code()} ${error ?: response.message()}"
                    )
                }

                val body = response.body()
                    ?: return ResultadoFieldOpsApi.Error("El servidor respondió vacío en programas campo")

                todos.addAll(body.results)

                if (body.next.isNullOrBlank()) {
                    break
                }

                page++
            }

            ResultadoFieldOpsApi.Exito(todos)
        } catch (e: Exception) {
            ResultadoFieldOpsApi.Error(
                "No se pudieron cargar programas campo: ${e.message}"
            )
        }
    }
}

sealed class ResultadoFieldOpsApi {
    data class Exito(
        val programas: List<FieldTaskApiItem>
    ) : ResultadoFieldOpsApi()

    data class Error(
        val mensaje: String
    ) : ResultadoFieldOpsApi()
}
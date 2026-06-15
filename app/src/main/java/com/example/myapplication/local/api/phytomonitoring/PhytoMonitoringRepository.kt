package com.example.myapplication.local.api.phytomonitoring

import android.content.Context
import com.example.myapplication.local.api.core.RetrofitClient

class PhytoMonitoringRepository(
    context: Context
) {
    private val api: PhytoMonitoringApiService =
        RetrofitClient.crearServicioAutenticado(
            context = context,
            serviceClass = PhytoMonitoringApiService::class.java
        )

    suspend fun obtenerTodosLosHeaders(): ResultadoPhytoHeadersApi {
        return try {
            val todos = mutableListOf<PhytoHeaderApiItem>()
            var page = 1

            while (true) {
                val response = api.listarHeaders(page = page)

                if (!response.isSuccessful) {
                    val error = response.errorBody()?.string()
                    return ResultadoPhytoHeadersApi.Error(
                        "Error headers fitosanitarios: ${response.code()} ${error ?: response.message()}"
                    )
                }

                val body = response.body()
                    ?: return ResultadoPhytoHeadersApi.Error("El servidor respondió vacío en headers fitosanitarios")

                todos.addAll(body.results)

                if (body.next.isNullOrBlank()) {
                    break
                }

                page++
            }

            ResultadoPhytoHeadersApi.Exito(todos)
        } catch (e: Exception) {
            ResultadoPhytoHeadersApi.Error(
                "No se pudieron cargar headers fitosanitarios: ${e.message}"
            )
        }
    }

    suspend fun obtenerTodosLosTargetPoints(): ResultadoPhytoTargetPointsApi {
        return try {
            val todos = mutableListOf<PhytoTargetPointApiItem>()
            var page = 1

            while (true) {
                val response = api.listarTargetPoints(page = page)

                if (!response.isSuccessful) {
                    val error = response.errorBody()?.string()
                    return ResultadoPhytoTargetPointsApi.Error(
                        "Error puntos objetivo: ${response.code()} ${error ?: response.message()}"
                    )
                }

                val body = response.body()
                    ?: return ResultadoPhytoTargetPointsApi.Error("El servidor respondió vacío en puntos objetivo")

                todos.addAll(body.results)

                if (body.next.isNullOrBlank()) {
                    break
                }

                page++
            }

            ResultadoPhytoTargetPointsApi.Exito(todos)
        } catch (e: Exception) {
            ResultadoPhytoTargetPointsApi.Error(
                "No se pudieron cargar puntos objetivo: ${e.message}"
            )
        }
    }
}

sealed class ResultadoPhytoHeadersApi {
    data class Exito(
        val headers: List<PhytoHeaderApiItem>
    ) : ResultadoPhytoHeadersApi()

    data class Error(
        val mensaje: String
    ) : ResultadoPhytoHeadersApi()
}

sealed class ResultadoPhytoTargetPointsApi {
    data class Exito(
        val puntos: List<PhytoTargetPointApiItem>
    ) : ResultadoPhytoTargetPointsApi()

    data class Error(
        val mensaje: String
    ) : ResultadoPhytoTargetPointsApi()
}
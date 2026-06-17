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
                    ?: return ResultadoPhytoHeadersApi.Error(
                        "El servidor respondió vacío en headers fitosanitarios"
                    )

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
                    ?: return ResultadoPhytoTargetPointsApi.Error(
                        "El servidor respondió vacío en puntos objetivo"
                    )

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


    suspend fun obtenerHeaderPorExtId(idHeaderExt: String): ResultadoHeaderDetalleApi {
        return try {
            val response = api.obtenerHeaderDetalle(idHeaderExt)

            if (!response.isSuccessful) {
                val error = response.errorBody()?.string()
                return ResultadoHeaderDetalleApi.Error(
                    "Error detalle header: ${response.code()} ${error ?: response.message()}"
                )
            }

            val body = response.body()
                ?: return ResultadoHeaderDetalleApi.Error(
                    "El servidor respondió vacío en detalle header"
                )

            ResultadoHeaderDetalleApi.Exito(body)
        } catch (e: Exception) {
            ResultadoHeaderDetalleApi.Error(
                "No se pudo cargar detalle header: ${e.message}"
            )
        }
    }

    suspend fun obtenerHeadersCompletados(): ResultadoPhytoHeadersApi {
        return obtenerHeadersPorEstado("completed")
    }

    private suspend fun obtenerHeadersPorEstado(status: String): ResultadoPhytoHeadersApi {
        return try {
            val todos = mutableListOf<PhytoHeaderApiItem>()
            var page = 1

            while (true) {
                val response = api.listarHeaders(status = status, page = page)

                if (!response.isSuccessful) {
                    val error = response.errorBody()?.string()
                    return ResultadoPhytoHeadersApi.Error(
                        "Error headers $status: ${response.code()} ${error ?: response.message()}"
                    )
                }

                val body = response.body()
                    ?: return ResultadoPhytoHeadersApi.Error(
                        "El servidor respondió vacío en headers $status"
                    )

                todos.addAll(body.results)

                if (body.next.isNullOrBlank()) {
                    break
                }

                page++
            }

            ResultadoPhytoHeadersApi.Exito(todos)
        } catch (e: Exception) {
            ResultadoPhytoHeadersApi.Error(
                "No se pudieron cargar headers $status: ${e.message}"
            )
        }
    }

    suspend fun actualizarHeaderServidor(
        idHeaderExt: String,
        status: String? = null,
        startedAt: String? = null,
        finishedAt: String? = null,
        additionalNotes: String? = null
    ): ResultadoActualizarHeaderApi {
        return try {
            val response = api.actualizarHeader(
                id = idHeaderExt,
                body = PhytoHeaderPatchRequest(
                    status = status,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    additionalNotes = additionalNotes
                )
            )

            if (!response.isSuccessful) {
                val error = response.errorBody()?.string()
                return ResultadoActualizarHeaderApi.Error(
                    "Error actualizando header: ${response.code()} ${error ?: response.message()}"
                )
            }

            val body = response.body()
                ?: return ResultadoActualizarHeaderApi.Error(
                    "Servidor respondió vacío al actualizar header"
                )

            ResultadoActualizarHeaderApi.Exito(body)
        } catch (e: Exception) {
            ResultadoActualizarHeaderApi.Error(
                "No se pudo actualizar header en servidor: ${e.message}"
            )
        }
    }
}

sealed class ResultadoHeaderDetalleApi {
    data class Exito(
        val header: PhytoHeaderApiItem
    ) : ResultadoHeaderDetalleApi()

    data class Error(
        val mensaje: String
    ) : ResultadoHeaderDetalleApi()
}

sealed class ResultadoActualizarHeaderApi {
    data class Exito(
        val header: PhytoHeaderApiItem
    ) : ResultadoActualizarHeaderApi()

    data class Error(
        val mensaje: String
    ) : ResultadoActualizarHeaderApi()
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
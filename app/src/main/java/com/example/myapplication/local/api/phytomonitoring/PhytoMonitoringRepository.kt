package com.example.myapplication.local.api.phytomonitoring

import android.content.Context
import com.example.myapplication.local.api.core.RetrofitClient

class PhytoMonitoringRepository(
    context: Context
) {
    private val api: PhytoMonitoringApiService =
        RetrofitClient.crearServicioAutenticado(
            context = context.applicationContext,
            serviceClass = PhytoMonitoringApiService::class.java
        )

    suspend fun obtenerTodosLosHeaders(
        assignedTo: String? = null,
        estimatedStartDate: String? = null,
        fieldTask: String? = null,
        plot: String? = null,
        status: String? = null
    ): ResultadoPhytoHeadersApi {
        return try {
            val todos = mutableListOf<PhytoHeaderApiItem>()
            var page = 1

            while (true) {
                val response = api.listarHeaders(
                    assignedTo = assignedTo,
                    estimatedStartDate = estimatedStartDate,
                    fieldTask = fieldTask,
                    plot = plot,
                    status = status,
                    page = page
                )

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

                if (body.next.isNullOrBlank()) break
                page++
            }

            ResultadoPhytoHeadersApi.Exito(todos)
        } catch (e: Exception) {
            ResultadoPhytoHeadersApi.Error(
                "No se pudieron cargar headers fitosanitarios: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    suspend fun obtenerHeaderDetalle(
        id: String
    ): ResultadoPhytoHeadersApi {
        return try {
            val response = api.obtenerHeaderDetalle(id)

            if (!response.isSuccessful) {
                val error = response.errorBody()?.string()
                return ResultadoPhytoHeadersApi.Error(
                    "Error detalle header $id: ${response.code()} ${error ?: response.message()}"
                )
            }

            val header = response.body()
                ?: return ResultadoPhytoHeadersApi.Error(
                    "El servidor respondio vacio al consultar el header $id"
                )

            ResultadoPhytoHeadersApi.Exito(listOf(header))
        } catch (e: Exception) {
            ResultadoPhytoHeadersApi.Error(
                "No se pudo cargar el detalle del header $id: " +
                        (e.message ?: e.javaClass.simpleName)
            )
        }
    }

    /**
     * Crea la sesión fitosanitaria remota, vinculada a un Programa ya creado.
     */
    suspend fun crearHeader(
        body: PhytoHeaderCreateRequest
    ): ResultadoCrearPhytoHeaderApi {
        return try {
            val response = api.crearHeader(body)

            if (!response.isSuccessful) {
                val error = response.errorBody()?.string()
                return ResultadoCrearPhytoHeaderApi.Error(
                    "Error creando sesión fitosanitaria: ${response.code()} ${error ?: response.message()}"
                )
            }

            val header = response.body()
                ?: return ResultadoCrearPhytoHeaderApi.Error(
                    "El servidor creó la sesión, pero respondió sin información."
                )

            if (header.id.isBlank()) {
                return ResultadoCrearPhytoHeaderApi.Error(
                    "El servidor respondió una sesión sin UUID."
                )
            }

            ResultadoCrearPhytoHeaderApi.Exito(header)
        } catch (e: Exception) {
            ResultadoCrearPhytoHeaderApi.Error(
                "No se pudo crear la sesión fitosanitaria: ${e.message ?: e.javaClass.simpleName}"
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

                if (body.next.isNullOrBlank()) break
                page++
            }

            ResultadoPhytoTargetPointsApi.Exito(todos)
        } catch (e: Exception) {
            ResultadoPhytoTargetPointsApi.Error(
                "No se pudieron cargar puntos objetivo: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Actualiza el ciclo de vida real de un monitoreo en Django.
     * Los valores que recibe la API son: pending, in_progress, completed, cancelled.
     */
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
                "No se pudo actualizar header en servidor: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }
}

sealed class ResultadoCrearPhytoHeaderApi {
    data class Exito(
        val header: PhytoHeaderApiItem
    ) : ResultadoCrearPhytoHeaderApi()

    data class Error(
        val mensaje: String
    ) : ResultadoCrearPhytoHeaderApi()
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

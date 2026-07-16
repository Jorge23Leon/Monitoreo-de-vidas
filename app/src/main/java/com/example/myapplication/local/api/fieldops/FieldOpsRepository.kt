package com.example.myapplication.local.api.fieldops

import android.content.Context
import com.example.myapplication.local.api.core.RetrofitClient

class FieldOpsRepository(
    context: Context
) {
    private val api: FieldOpsApiService =
        RetrofitClient.crearServicioAutenticado(
            context = context.applicationContext,
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
                    ?: return ResultadoFieldOpsApi.Error(
                        "El servidor respondió vacío en programas campo"
                    )

                todos.addAll(body.results)

                if (body.next.isNullOrBlank()) break
                page++
            }

            ResultadoFieldOpsApi.Exito(todos)
        } catch (e: Exception) {
            ResultadoFieldOpsApi.Error(
                "No se pudieron cargar programas campo: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Crea el Programa remoto. La pantalla de administración debe usar este método
     * antes de crear el Header fitosanitario.
     */
    suspend fun crearProgramaCampo(
        body: FieldTaskCreateRequest
    ): ResultadoCrearFieldTaskApi {
        return try {
            val response = api.crearProgramaCampo(body)

            if (!response.isSuccessful) {
                val error = response.errorBody()?.string()
                return ResultadoCrearFieldTaskApi.Error(
                    "Error creando programa remoto: ${response.code()} ${error ?: response.message()}"
                )
            }

            val programa = response.body()
                ?: return ResultadoCrearFieldTaskApi.Error(
                    "El servidor creó el programa, pero respondió sin información."
                )

            if (programa.id.isBlank()) {
                return ResultadoCrearFieldTaskApi.Error(
                    "El servidor respondió un programa sin UUID."
                )
            }

            ResultadoCrearFieldTaskApi.Exito(programa)
        } catch (e: Exception) {
            ResultadoCrearFieldTaskApi.Error(
                "No se pudo crear el programa remoto: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Corrige un Programa remoto que fue creado o reutilizado sin título visible.
     * Esto evita que en el admin de Django aparezca con Title "-".
     */
    suspend fun actualizarProgramaCampo(
        id: String,
        body: FieldTaskPatchRequest
    ): ResultadoCrearFieldTaskApi {
        return try {
            val response = api.actualizarProgramaCampo(
                id = id,
                body = body
            )

            if (!response.isSuccessful) {
                val error = response.errorBody()?.string()
                return ResultadoCrearFieldTaskApi.Error(
                    "Error actualizando programa remoto: ${response.code()} ${error ?: response.message()}"
                )
            }

            val programa = response.body()
                ?: return ResultadoCrearFieldTaskApi.Error(
                    "El servidor actualizó el programa, pero respondió sin información."
                )

            if (programa.id.isBlank()) {
                return ResultadoCrearFieldTaskApi.Error(
                    "El servidor respondió un programa actualizado sin UUID."
                )
            }

            ResultadoCrearFieldTaskApi.Exito(programa)
        } catch (e: Exception) {
            ResultadoCrearFieldTaskApi.Error(
                "No se pudo actualizar el programa remoto: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    suspend fun obtenerTodosLosProgramasMaestros(
        datacentral: String? = null,
        agroUnit: String? = null,
        status: String? = null
    ): ResultadoMasterProgramsApi {
        return try {
            val todos = mutableListOf<MasterProgramApiItem>()
            var page = 1

            while (true) {
                val response = api.listarProgramasMaestros(
                    datacentral = datacentral,
                    agroUnit = agroUnit,
                    status = status,
                    page = page
                )

                if (!response.isSuccessful) {
                    val error = response.errorBody()?.string()
                    return ResultadoMasterProgramsApi.Error(
                        "Error programas maestros: ${response.code()} ${error ?: response.message()}"
                    )
                }

                val body = response.body()
                    ?: return ResultadoMasterProgramsApi.Error(
                        "El servidor respondió vacío en programas maestros"
                    )

                todos.addAll(body.results)

                if (body.next.isNullOrBlank()) break
                page++
            }

            ResultadoMasterProgramsApi.Exito(todos)
        } catch (e: Exception) {
            ResultadoMasterProgramsApi.Error(
                "No se pudieron cargar programas maestros: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    suspend fun obtenerArbolProgramaMaestro(
        id: String
    ): ResultadoMasterProgramTreeApi {
        return try {
            val response = api.obtenerArbolProgramaMaestro(id)

            if (!response.isSuccessful) {
                val error = response.errorBody()?.string()
                return ResultadoMasterProgramTreeApi.Error(
                    "Error árbol programa maestro: ${response.code()} ${error ?: response.message()}"
                )
            }

            val body = response.body()
                ?: return ResultadoMasterProgramTreeApi.Error(
                    "El servidor respondió vacío en árbol programa maestro"
                )

            ResultadoMasterProgramTreeApi.Exito(body)
        } catch (e: Exception) {
            ResultadoMasterProgramTreeApi.Error(
                "No se pudo cargar árbol programa maestro: ${e.message ?: e.javaClass.simpleName}"
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

sealed class ResultadoCrearFieldTaskApi {
    data class Exito(
        val programa: FieldTaskApiItem
    ) : ResultadoCrearFieldTaskApi()

    data class Error(
        val mensaje: String
    ) : ResultadoCrearFieldTaskApi()
}

sealed class ResultadoMasterProgramsApi {
    data class Exito(
        val programasMaestros: List<MasterProgramApiItem>
    ) : ResultadoMasterProgramsApi()

    data class Error(
        val mensaje: String
    ) : ResultadoMasterProgramsApi()
}

sealed class ResultadoMasterProgramTreeApi {
    data class Exito(
        val programaMaestro: MasterProgramTreeApiItem
    ) : ResultadoMasterProgramTreeApi()

    data class Error(
        val mensaje: String
    ) : ResultadoMasterProgramTreeApi()
}

package com.example.myapplication.local.api.users

import android.content.Context
import com.example.myapplication.local.api.core.RetrofitClient

class UserRepository(
    context: Context
) {
    private val userApiService: UserApiService =
        RetrofitClient.crearServicioAutenticado(
            context = context,
            serviceClass = UserApiService::class.java
        )

    suspend fun obtenerPerfilActual(): ResultadoUsuarioMeApi {
        return try {
            val response = userApiService.obtenerPerfilActual()

            if (response.isSuccessful) {
                val body = response.body()

                if (body == null) {
                    ResultadoUsuarioMeApi.Error("El servidor respondió vacío")
                } else {
                    ResultadoUsuarioMeApi.Exito(body)
                }
            } else {
                val error = response.errorBody()?.string()
                ResultadoUsuarioMeApi.Error(
                    "No se pudo obtener perfil: ${response.code()} ${error ?: response.message()}"
                )
            }
        } catch (e: Exception) {
            ResultadoUsuarioMeApi.Error(
                "Error conectando con perfil: ${e.message}"
            )
        }
    }
}

sealed class ResultadoUsuarioMeApi {
    data class Exito(
        val usuario: UsuarioMeResponse
    ) : ResultadoUsuarioMeApi()

    data class Error(
        val mensaje: String
    ) : ResultadoUsuarioMeApi()
}
package com.example.myapplication.local.api.auth

import android.content.Context
import com.example.myapplication.local.api.core.RetrofitClient

class AuthRepository(
    context: Context? = null,
    private val authApiServicePublico: AuthApiService = RetrofitClient.authApiService
) {

    private val authApiServiceAutenticado: AuthApiService =
        if (context != null) {
            RetrofitClient.crearServicioAutenticado(
                context = context,
                serviceClass = AuthApiService::class.java
            )
        } else {
            authApiServicePublico
        }

    suspend fun signup(
        username: String,
        email: String,
        password: String,
        firstName: String,
        lastName: String
    ): ResultadoSignupApi {
        return try {
            val response = authApiServicePublico.signup(
                SignupRequest(
                    username = username,
                    email = email,
                    password = password,
                    first_name = firstName,
                    last_name = lastName
                )
            )

            if (response.isSuccessful) {
                ResultadoSignupApi.Exito(
                    mensaje = "Usuario registrado correctamente"
                )
            } else {
                val error = response.errorBody()?.string()
                ResultadoSignupApi.Error(
                    "Registro falló: ${response.code()} ${error ?: response.message()}"
                )
            }
        } catch (e: Exception) {
            ResultadoSignupApi.Error(
                "No se pudo registrar en servidor: ${e.message}"
            )
        }
    }

    suspend fun login(
        username: String,
        password: String
    ): ResultadoLoginApi {
        return try {
            val response = authApiServicePublico.login(
                LoginRequest(
                    username = username,
                    password = password
                )
            )

            if (response.isSuccessful) {
                val body = response.body()

                if (body == null) {
                    ResultadoLoginApi.Error("El servidor respondió vacío")
                } else {
                    ResultadoLoginApi.Exito(
                        access = body.access,
                        refresh = body.refresh
                    )
                }
            } else {
                val error = response.errorBody()?.string()
                ResultadoLoginApi.Error(
                    "Login falló: ${response.code()} ${error ?: response.message()}"
                )
            }
        } catch (e: Exception) {
            ResultadoLoginApi.Error(
                "No se pudo conectar al servidor: ${e.message}"
            )
        }
    }

    suspend fun obtenerAccessConRefresh(
        refresh: String
    ): ResultadoRefreshApi {
        return try {
            val response = authApiServicePublico.refresh(
                RefreshRequest(refresh = refresh)
            )

            if (response.isSuccessful) {
                val access = response.body()?.access

                if (access.isNullOrBlank()) {
                    ResultadoRefreshApi.Error("El servidor no regresó access token")
                } else {
                    ResultadoRefreshApi.Exito(access)
                }
            } else {
                val error = response.errorBody()?.string()
                ResultadoRefreshApi.Error(
                    "Refresh falló: ${response.code()} ${error ?: response.message()}"
                )
            }
        } catch (e: Exception) {
            ResultadoRefreshApi.Error(
                "No se pudo refrescar token: ${e.message}"
            )
        }
    }

    suspend fun logout(
        refresh: String
    ): ResultadoLogoutApi {
        return try {
            val response = authApiServiceAutenticado.logout(
                LogoutRequest(refresh = refresh)
            )

            if (response.isSuccessful) {
                ResultadoLogoutApi.Exito
            } else {
                val error = response.errorBody()?.string()
                ResultadoLogoutApi.Error(
                    "Logout falló: ${response.code()} ${error ?: response.message()}"
                )
            }
        } catch (e: Exception) {
            ResultadoLogoutApi.Error(
                "No se pudo cerrar sesión en servidor: ${e.message}"
            )
        }
    }
}

sealed class ResultadoLoginApi {
    data class Exito(
        val access: String?,
        val refresh: String?
    ) : ResultadoLoginApi()

    data class Error(
        val mensaje: String
    ) : ResultadoLoginApi()
}

sealed class ResultadoRefreshApi {
    data class Exito(
        val access: String
    ) : ResultadoRefreshApi()

    data class Error(
        val mensaje: String
    ) : ResultadoRefreshApi()
}

sealed class ResultadoLogoutApi {
    object Exito : ResultadoLogoutApi()

    data class Error(
        val mensaje: String
    ) : ResultadoLogoutApi()
}

sealed class ResultadoSignupApi {
    data class Exito(
        val mensaje: String
    ) : ResultadoSignupApi()

    data class Error(
        val mensaje: String
    ) : ResultadoSignupApi()
}
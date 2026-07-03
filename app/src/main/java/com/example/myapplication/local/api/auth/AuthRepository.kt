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
                context = context.applicationContext,
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

                if (body == null || body.access.isNullOrBlank() || body.refresh.isNullOrBlank()) {
                    ResultadoLoginApi.Error(
                        "No se pudo iniciar sesión. Intenta nuevamente."
                    )
                } else {
                    ResultadoLoginApi.Exito(
                        access = body.access,
                        refresh = body.refresh
                    )
                }
            } else {
                when (response.code()) {
                    400, 401, 403 -> {
                        ResultadoLoginApi.Error(
                            "Usuario o contraseña incorrectos."
                        )
                    }

                    else -> {
                        ResultadoLoginApi.Error(
                            "No se pudo iniciar sesión. Intenta nuevamente."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            ResultadoLoginApi.Error(
                "No se pudo conectar al servidor. Revisa tu conexión e inténtalo nuevamente."
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

    suspend fun cambiarPassword(
        oldPassword: String,
        newPassword: String
    ): ResultadoCambiarPasswordApi {
        return try {
            val response = authApiServiceAutenticado.changePassword(
                ChangePasswordRequest(
                    old_password = oldPassword,
                    new_password = newPassword
                )
            )

            if (response.isSuccessful) {
                ResultadoCambiarPasswordApi.Exito(
                    mensaje = response.body()?.detail
                        ?.takeIf { it.isNotBlank() }
                        ?: "Contraseña actualizada correctamente."
                )
            } else {
                when (response.code()) {
                    400 -> ResultadoCambiarPasswordApi.Error(
                        "La contraseña actual no es correcta."
                    )

                    401, 403 -> ResultadoCambiarPasswordApi.Error(
                        "Tu sesión venció. Inicia sesión nuevamente."
                    )

                    else -> ResultadoCambiarPasswordApi.Error(
                        "No se pudo actualizar la contraseña. Intenta nuevamente."
                    )
                }
            }
        } catch (e: Exception) {
            ResultadoCambiarPasswordApi.Error(
                "No se pudo conectar al servidor. Revisa tu conexión e inténtalo nuevamente."
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

sealed class ResultadoCambiarPasswordApi {
    data class Exito(
        val mensaje: String
    ) : ResultadoCambiarPasswordApi()

    data class Error(
        val mensaje: String
    ) : ResultadoCambiarPasswordApi()
}

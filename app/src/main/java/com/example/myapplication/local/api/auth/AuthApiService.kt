package com.example.myapplication.local.api.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {

    // Sirve para iniciar sesión.

    @POST("api/v1/auth/login/")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>


     // Sirve para renovar el access token cuando vence.

    @POST("api/v1/auth/refresh/")
    suspend fun refresh(
        @Body request: RefreshRequest
    ): Response<RefreshResponse>

    //Sirve para cerrar sesión en servidor.

    @POST("api/v1/auth/logout/")
    suspend fun logout(
        @Body request: LogoutRequest
    ): Response<Unit>


     //Sirve para registrar usuario público.

    @POST("api/v1/auth/signup/")
    suspend fun signup(
        @Body request: SignupRequest
    ): Response<SignupResponse>

    //Sirve para cambiar contraseña una vez logueado.

    @POST("api/v1/auth/change-password/")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest
    ): Response<ChangePasswordResponse>
}
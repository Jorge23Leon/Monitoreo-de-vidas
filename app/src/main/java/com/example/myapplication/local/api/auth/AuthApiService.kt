package com.example.myapplication.local.api.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {

    @POST("api/v1/auth/login/")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("api/v1/auth/refresh/")
    suspend fun refresh(
        @Body request: RefreshRequest
    ): Response<RefreshResponse>

    @POST("api/v1/auth/logout/")
    suspend fun logout(
        @Body request: LogoutRequest
    ): Response<Unit>


    @POST("api/v1/auth/signup/")
    suspend fun signup(
        @Body request: SignupRequest
    ): Response<SignupResponse>
}
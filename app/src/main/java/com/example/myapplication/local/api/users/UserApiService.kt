package com.example.myapplication.local.api.users

import retrofit2.Response
import retrofit2.http.GET

interface UserApiService {

    @GET("api/v1/users/me/")
    suspend fun obtenerPerfilActual(): Response<UsuarioMeResponse>
}
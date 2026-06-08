package com.example.myapplication.local.api.auth

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val access: String? = null,
    val refresh: String? = null,
    val detail: String? = null
)

data class RefreshRequest(
    val refresh: String
)

data class RefreshResponse(
    val access: String? = null,
    val detail: String? = null
)

data class LogoutRequest(
    val refresh: String
)
data class SignupRequest(
    val username: String,
    val email: String,
    val password: String,
    val first_name: String,
    val last_name: String
)

data class SignupResponse(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val status: String? = null,
    val detail: String? = null
)
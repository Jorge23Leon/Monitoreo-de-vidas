package com.example.myapplication.local.api.auth

/*
 * Body que se manda al login.
 */
data class LoginRequest(
    val username: String,
    val password: String
)

/*
 * Respuesta del login.
 */
data class LoginResponse(
    val access: String? = null,
    val refresh: String? = null,
    val detail: String? = null
)

/*
 * Body para renovar token.
  */
data class RefreshRequest(
    val refresh: String
)

/*
 * Respuesta del refresh.
 */
data class RefreshResponse(
    val access: String? = null,
    val detail: String? = null
)

/*
 * Body para logout.
 *
 * Se manda el refresh para invalidarlo en backend.
 */
data class LogoutRequest(
    val refresh: String
)

/*
 * Body para registro público.
 */
data class SignupRequest(
    val username: String,
    val email: String,
    val password: String,
    val first_name: String,
    val last_name: String
)

/*
 * Respuesta del signup.
 */
data class SignupResponse(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val status: String? = null,
    val detail: String? = null
)

/*
 * Body para cambiar contraseña.
 */
data class ChangePasswordRequest(
    val old_password: String,
    val new_password: String
)

/*
 * Respuesta del cambio de contraseña.
 */
data class ChangePasswordResponse(
    val detail: String? = null
)
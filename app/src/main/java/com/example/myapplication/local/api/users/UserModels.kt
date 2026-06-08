package com.example.myapplication.local.api.users

import com.google.gson.annotations.SerializedName

data class UsuarioMeResponse(
    val id: String,
    val username: String?,
    val email: String?,
    val status: String?,
    @SerializedName("user_role")
    val userRole: UserRoleResponse?,
    @SerializedName("requires_password_change")
    val requiresPasswordChange: Boolean?,
    @SerializedName("is_active")
    val isActive: Boolean?,
    val individual: IndividualResponse?,
    val datacentrals: List<DataCentralResponse>?
)

data class UserRoleResponse(
    val id: String? = null,
    val name: String? = null,
    val level: Int? = null
)

data class IndividualResponse(
    @SerializedName("first_name")
    val firstName: String? = null,
    @SerializedName("last_name")
    val lastName: String? = null,
    val phone: String? = null,
    @SerializedName("personal_email")
    val personalEmail: String? = null,
    @SerializedName("photo_url")
    val photoUrl: String? = null
)

data class DataCentralResponse(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    @SerializedName("is_owner")
    val isOwner: Boolean? = null
)
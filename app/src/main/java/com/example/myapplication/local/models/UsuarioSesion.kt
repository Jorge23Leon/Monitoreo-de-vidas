package com.example.myapplication.local.models

import java.util.Locale

data class UsuarioSesion(
    val idUser: Long,
    val extId: String?,
    val firstName: String,
    val lastName: String?,
    val username: String,
    val email: String,

    val idRole: Long,
    val roleName: String,
    val level: Int
) {
    val rolNormalizado: String
        get() {
            val limpio = roleName
                .trim()
                .lowercase(Locale.getDefault())
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace(".", "")
                .replace("_", " ")
                .replace(Regex("\\s+"), " ")

            return when (limpio) {
                "super admin", "admin", "administrador" -> "admin"
                "gerente" -> "gerente"
                "ingy supervision", "ing y supervision", "supervisor" -> "supervisor"
                "tecnico", "tecnicos", "técnico", "técnicos" -> "tecnico"
                "invitado" -> "invitado"
                else -> limpio
            }
        }

    val esAdmin: Boolean
        get() = rolNormalizado == "admin"

    val esGerente: Boolean
        get() = rolNormalizado == "gerente"

    val esSupervisor: Boolean
        get() = rolNormalizado == "supervisor"

    val esTecnico: Boolean
        get() = rolNormalizado == "tecnico"

    val esInvitado: Boolean
        get() = rolNormalizado == "invitado"
}
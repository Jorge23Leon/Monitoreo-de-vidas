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

    /** SUPER ADMIN puede entrar a todas las CIAS padre e hijas. */
    val puedeEntrarATodasLasCias: Boolean
        get() = esAdmin

    /** Panel de trabajo: no entra INVITADO. */
    val puedeVerPanelTrabajo: Boolean
        get() = esAdmin || esGerente || esSupervisor || esTecnico

    /** Solo SUPER ADMIN modifica productores, ranchos, parcelas y vértices. */
    val puedeGestionAgricola: Boolean
        get() = esAdmin

    /** Solo SUPER ADMIN modifica catálogos. */
    val puedeGestionCatalogos: Boolean
        get() = esAdmin

    /** Gerente, supervisor y técnico pueden crear / dar de alta monitoreos. */
    val puedeCrearMonitoreos: Boolean
        get() = esAdmin || esGerente || esSupervisor || esTecnico

    /** Todos pueden capturar, pero el invitado solo verá lo asignado o lo hecho por él. */
    val puedeCapturarMonitoreos: Boolean
        get() = esAdmin || esGerente || esSupervisor || esTecnico || esInvitado

    /** Técnico trabaja con parcelas asignadas. */
    val soloParcelasAsignadas: Boolean
        get() = esTecnico

    /** Invitado solo ve monitoreos asignados o capturados por él. */
    val soloSusMonitoreos: Boolean
        get() = esInvitado
}

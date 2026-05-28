package com.example.myapplication.local.core

import java.util.Locale

fun normalizarRolVm(rol: String): String {
    val limpio = rol
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

fun esRolAdministradorVm(rol: String): Boolean = normalizarRolVm(rol) == "admin"
fun esRolGerenteVm(rol: String): Boolean = normalizarRolVm(rol) == "gerente"
fun esRolSupervisorVm(rol: String): Boolean = normalizarRolVm(rol) == "supervisor"
fun esRolTecnicoVm(rol: String): Boolean = normalizarRolVm(rol) == "tecnico"
fun esRolInvitadoVm(rol: String): Boolean = normalizarRolVm(rol) == "invitado"

fun puedeVerPanelTrabajoVm(rol: String): Boolean {
    return esRolAdministradorVm(rol)
}

fun puedeCrearMonitoreosVm(rol: String): Boolean {
    return esRolAdministradorVm(rol)
}

fun puedeGestionCatalogosVm(rol: String): Boolean {
    return esRolAdministradorVm(rol)
}

fun puedeGestionAgricolaVm(rol: String): Boolean {
    return esRolAdministradorVm(rol)
}
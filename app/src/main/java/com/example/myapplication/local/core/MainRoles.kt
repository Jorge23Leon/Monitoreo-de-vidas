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

fun esRolAdministradorVm(rol: String): Boolean {
    return normalizarRolVm(rol) == "admin"
}

fun esRolGerenteVm(rol: String): Boolean {
    return normalizarRolVm(rol) == "gerente"
}

fun esRolSupervisorVm(rol: String): Boolean {
    return normalizarRolVm(rol) == "supervisor"
}

fun esRolTecnicoVm(rol: String): Boolean {
    return normalizarRolVm(rol) == "tecnico"
}

fun esRolInvitadoVm(rol: String): Boolean {
    return normalizarRolVm(rol) == "invitado"
}

fun puedeVerPanelTrabajoVm(rol: String): Boolean {
    return esRolAdministradorVm(rol) ||
            esRolGerenteVm(rol) ||
            esRolSupervisorVm(rol)
}

fun puedeCrearMonitoreosVm(rol: String): Boolean {
    return esRolAdministradorVm(rol) ||
            esRolGerenteVm(rol) ||
            esRolSupervisorVm(rol)
}

fun puedeGestionCatalogosVm(rol: String): Boolean {
    return esRolAdministradorVm(rol)
}

fun puedeGestionAgricolaVm(rol: String): Boolean {
    return esRolAdministradorVm(rol)
}

/*
 * Roles de consulta:
 * Gerente y supervisor pueden consultar monitoreos, pero no administrar datos.
 */
fun esRolConsultaVm(rol: String): Boolean {
    return esRolGerenteVm(rol) || esRolSupervisorVm(rol)
}

/*
 * Roles operativos:
 * Técnico captura monitoreos asignados.
 * Invitado solo consulta lo asignado o capturado por él.
 */
fun esRolOperativoVm(rol: String): Boolean {
    return esRolTecnicoVm(rol) || esRolInvitadoVm(rol)
}
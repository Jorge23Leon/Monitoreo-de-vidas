package com.example.myapplication.local.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsuarioSesionTest {

    @Test
    fun backendSuperAdminNameIsRecognized() {
        val user = user(roleName = "SuperAdmin", level = 5)

        assertEquals("admin", user.rolNormalizado)
        assertTrue(user.esAdmin)
        assertFalse(user.esInvitado)
    }

    @Test
    fun roleLevelRemainsCanonicalWhenDisplayNameChanges() {
        assertTrue(user(roleName = "Coordinación global", level = 5).esAdmin)
        assertTrue(user(roleName = "Mando regional", level = 4).esGerente)
        assertTrue(user(roleName = "Campo", level = 3).esSupervisor)
        assertTrue(user(roleName = "Operador", level = 2).esTecnico)
        assertTrue(user(roleName = "Consulta", level = 1).esInvitado)
    }

    @Test
    fun onlyAdminAndManagerCanSeeAspersion() {
        assertTrue(user(roleName = "SUPER ADMIN", level = 5).puedeVerAspersion)
        assertTrue(user(roleName = "GERENTE", level = 4).puedeVerAspersion)
        assertFalse(user(roleName = "SUPERVISOR", level = 3).puedeVerAspersion)
        assertFalse(user(roleName = "TECNICO", level = 2).puedeVerAspersion)
        assertFalse(user(roleName = "INVITADO", level = 1).puedeVerAspersion)
    }

    private fun user(roleName: String, level: Int) = UsuarioSesion(
        idUser = 1L,
        extId = "user-id",
        firstName = "Prueba",
        lastName = null,
        username = "prueba",
        email = "prueba@example.com",
        idRole = level.toLong(),
        roleName = roleName,
        level = level
    )
}

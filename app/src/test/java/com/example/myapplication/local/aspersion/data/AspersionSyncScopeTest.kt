package com.example.myapplication.local.aspersion.data

import com.example.myapplication.local.entities.LocalAspersionSessionEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AspersionSyncScopeTest {

    @Test
    fun adminFullRefreshMayRemoveAnyStaleSession() {
        assertTrue(session("s1").belongsToAspersionSyncScope(null, null))
    }

    @Test
    fun managerRefreshOnlyRemovesRowsFromSelectedCia() {
        assertTrue(
            session("s1", cias = "cia-a,cia-b")
                .belongsToAspersionSyncScope("cia-a", null)
        )
        assertFalse(
            session("s2", cias = "cia-b")
                .belongsToAspersionSyncScope("cia-a", null)
        )
    }

    @Test
    fun technicianRefreshOnlyRemovesRowsPreviouslyAssignedToTechnician() {
        assertTrue(
            session("s1", assignedTo = "user-a")
                .belongsToAspersionSyncScope(null, "user-a")
        )
        assertFalse(
            session("s2", assignedTo = "user-b")
                .belongsToAspersionSyncScope(null, "user-a")
        )
    }

    @Test
    fun legacyRowWithoutCiaContextIsCleanedAfterScopedRefresh() {
        assertTrue(
            session("legacy")
                .belongsToAspersionSyncScope("cia-a", null)
        )
    }

    private fun session(
        id: String,
        cias: String? = null,
        assignedTo: String? = null
    ) = LocalAspersionSessionEntity(
        sessionId = id,
        dataCentralIds = cias,
        assignedToId = assignedTo
    )
}

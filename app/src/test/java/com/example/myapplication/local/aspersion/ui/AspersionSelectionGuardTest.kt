package com.example.myapplication.local.aspersion.ui

import org.junit.Assert.assertFalse
import org.junit.Test

class AspersionSelectionGuardTest {

    @Test
    fun lateResultFromPreviousSessionIsRejected() {
        val accepted = isAspersionSelectionCurrent(
            currentSessionId = "session-b",
            requestedSessionId = "session-a",
            currentSelectionVersion = 2L,
            requestedSelectionVersion = 1L,
            currentScopeVersion = 1L,
            requestedScopeVersion = 1L
        )

        assertFalse(accepted)
    }

    @Test
    fun lateResultFromPreviousUserScopeIsRejected() {
        val accepted = isAspersionSelectionCurrent(
            currentSessionId = "session-a",
            requestedSessionId = "session-a",
            currentSelectionVersion = 3L,
            requestedSelectionVersion = 3L,
            currentScopeVersion = 2L,
            requestedScopeVersion = 1L
        )

        assertFalse(accepted)
    }
}

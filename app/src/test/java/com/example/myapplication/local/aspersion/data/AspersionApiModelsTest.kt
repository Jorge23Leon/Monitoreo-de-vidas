package com.example.myapplication.local.aspersion.data

import com.example.myapplication.local.aspersion.data.remote.AspersionPaginatedResponse
import com.example.myapplication.local.aspersion.data.remote.AspersionSessionDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AspersionApiModelsTest {

    @Test
    fun assignedUserObjectIsDeserializedFromRealApiShape() {
        val session = Gson().fromJson(
            """
            {
              "id": "session-1",
              "assigned_to": {
                "id": "user-1",
                "username": "kenya"
              },
              "points_count": 678
            }
            """.trimIndent(),
            AspersionSessionDto::class.java
        )

        assertEquals("user-1", session.assignedTo?.id)
        assertEquals("kenya", session.assignedTo?.username)
        assertEquals(678, session.pointsCount)
    }

    @Test
    fun nullableAssignedUserIsAccepted() {
        val session = Gson().fromJson(
            """{"id":"session-2","assigned_to":null,"points_count":0}""",
            AspersionSessionDto::class.java
        )

        assertNull(session.assignedTo)
    }

    @Test
    fun paginatedSessionResponseKeepsCountAndResults() {
        val type = object :
            TypeToken<AspersionPaginatedResponse<AspersionSessionDto>>() {}.type
        val response: AspersionPaginatedResponse<AspersionSessionDto> =
            Gson().fromJson(
                """
                {
                  "count": 1,
                  "next": null,
                  "previous": null,
                  "results": [{"id":"session-3","points_count":12}]
                }
                """.trimIndent(),
                type
            )

        assertEquals(1, response.count)
        assertEquals("session-3", response.results.single().id)
        assertEquals(12, response.results.single().pointsCount)
    }

    @Test
    fun hierarchyContextIsDeserializedForOfflineFilters() {
        val session = Gson().fromJson(
            """
            {
              "id": "session-context",
              "context": {
                "program": {"id":"program-1","name":"Purisima2","cycle":"Primavera-2026"},
                "plot": {"id":"plot-1","name":"Tabla 2","code":"T2"},
                "ranch": {"id":"ranch-1","name":"Purisima"},
                "producer": {"id":"producer-1","name":"Zona Norte"},
                "data_centrals": [{"id":"cia-1","name":"CIA Norte"}]
              }
            }
            """.trimIndent(),
            AspersionSessionDto::class.java
        )

        assertEquals("Purisima2", session.context?.program?.name)
        assertEquals("Tabla 2", session.context?.plot?.name)
        assertEquals("Zona Norte", session.context?.producer?.name)
        assertEquals("cia-1", session.context?.dataCentrals?.single()?.id)
    }
}

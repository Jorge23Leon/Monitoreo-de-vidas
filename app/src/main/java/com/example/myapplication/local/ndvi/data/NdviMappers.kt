package com.example.myapplication.local.ndvi.data

import com.example.myapplication.local.entities.LocalNdviPointEntity
import com.example.myapplication.local.entities.LocalNdviSessionEntity
import com.example.myapplication.local.ndvi.data.remote.NdviPointDto
import com.example.myapplication.local.ndvi.data.remote.NdviSessionDto
import com.google.gson.JsonElement


/**
 * Convierte una sesión recibida desde Django
 * a una sesión compatible con Room.
 *
 * existing:
 * sesión que ya estaba guardada localmente.
 *
 * La usamos para conservar datos exclusivamente locales,
 * como el estado de descarga offline.
 */
internal fun NdviSessionDto.toLocalEntity(
    existing: LocalNdviSessionEntity? = null,
    syncedAt: Long = System.currentTimeMillis()
): LocalNdviSessionEntity {

    val cleanSessionId = id.cleanRequired()

    return LocalNdviSessionEntity(

        sessionId = cleanSessionId,

        /**
         * Algunos endpoints pueden mandar:
         *
         * program
         *
         * o:
         *
         * program_id
         *
         * Preferimos programId y usamos program
         * como respaldo.
         */
        programId = programId.cleanNullable()
            ?: program.cleanNullable(),

        plotId = plot.cleanNullable(),

        sessionDate = sessionDate.cleanNullable(),

        status = status.cleanNullable(),

        assignedToId = assignedToId.cleanNullable()
            ?: assignedTo?.id.cleanNullable(),

        assignedToUsername = assignedTo
            ?.username
            .cleanNullable(),

        estStartDate = estStartDate.cleanNullable(),

        estFinishDate = estFinishDate.cleanNullable(),

        actStartDate = actStartDate.cleanNullable(),

        actFinishDate = actFinishDate.cleanNullable(),

        importStatus = importStatus.cleanNullable(),

        importErrorsJson = importErrors.toStoredJson(),

        importedAt = importedAt.cleanNullable(),

        /**
         * En nuestro DTO actual pointsCount se recibe
         * como String?, por eso lo convertimos aquí.
         */
        pointsCount = pointsCount
            ?.trim()
            ?.toIntOrNull(),

        contourStatus = contourStatus.cleanNullable(),

        createdAt = createdAt.cleanNullable(),

        updatedAt = updatedAt.cleanNullable(),

        /**
         * Estos campos NO vienen del backend.
         *
         * Si la sesión ya estaba descargada,
         * conservamos su estado local.
         */
        downloadedPointsCount =
        existing?.downloadedPointsCount ?: 0,

        pointsDownloadComplete =
        existing?.pointsDownloadComplete ?: false,

        lastSyncedAt = syncedAt
    )
}


/**
 * Convierte un punto NDVI recibido del backend
 * a una entidad local de Room.
 *
 * expectedSessionId:
 * UUID de la sesión que estamos sincronizando.
 *
 * Si el punto es inválido devuelve null.
 */
internal fun NdviPointDto.toLocalEntity(
    expectedSessionId: String
): LocalNdviPointEntity? {

    val cleanExpectedSessionId = expectedSessionId.cleanRequired()

    val cleanPointId = id.cleanNullable()
        ?: return null

    /**
     * Verificamos que el punto realmente pertenezca
     * a la sesión que estamos descargando.
     */
    val dtoSessionId = sessionHeader.cleanNullable()

    if (dtoSessionId != cleanExpectedSessionId) {
        return null
    }


    // =====================================================
    // GEOJSON
    // =====================================================

    /**
     * GeoJSON utiliza:
     *
     * coordinates[0] = LONGITUD
     * coordinates[1] = LATITUD
     */

    val longitude = geom
        ?.coordinates
        ?.getOrNull(0)
        ?.takeIf {
            it.isFinite() &&
                    it in -180.0..180.0
        }
        ?: return null

    val latitude = geom
        ?.coordinates
        ?.getOrNull(1)
        ?.takeIf {
            it.isFinite() &&
                    it in -90.0..90.0
        }
        ?: return null


    return LocalNdviPointEntity(

        pointId = cleanPointId,

        sessionId = cleanExpectedSessionId,

        geomType = geom
            ?.type
            .cleanNullable(),

        longitude = longitude,

        latitude = latitude,

        objId = objId,

        lotLabel = lotLabel.cleanNullable(),

        datasetDate = datasetDate.cleanNullable(),

        product = product.cleanNullable(),


        // =================================================
        // ÍNDICES VEGETATIVOS
        // =================================================

        ndvi = ndvi.finiteOrNull(),

        nirVigor = nirVigor.finiteOrNull(),

        osavi = osavi.finiteOrNull(),

        vari = vari.finiteOrNull(),

        bareSoilIndex = bareSoilIndex.finiteOrNull(),


        // =================================================
        // CANALES RGB
        // =================================================

        imageRed = imageRed.finiteOrNull(),

        imageGreen = imageGreen.finiteOrNull(),

        imageBlue = imageBlue.finiteOrNull(),


        // =================================================
        // ÍNDICES ADICIONALES
        // =================================================

        redEdge = redEdge.finiteOrNull(),

        swir = swir.finiteOrNull(),

        ndre = ndre.finiteOrNull(),

        msavi2 = msavi2.finiteOrNull(),

        gndvi = gndvi.finiteOrNull(),

        ndmi = ndmi.finiteOrNull(),

        psri = psri.finiteOrNull(),

        createdAt = createdAt.cleanNullable()
    )
}


/**
 * Convierte una lista completa de puntos.
 *
 * Los puntos inválidos se descartan automáticamente.
 */
internal fun List<NdviPointDto>.toLocalEntities(
    expectedSessionId: String
): List<LocalNdviPointEntity> {

    return mapNotNull { point ->

        point.toLocalEntity(
            expectedSessionId = expectedSessionId
        )
    }
}


// =========================================================
// FUNCIONES AUXILIARES
// =========================================================


/**
 * Limpia un String opcional.
 *
 * "  ndvi  " -> "ndvi"
 *
 * "" -> null
 */
private fun String?.cleanNullable(): String? {

    return this
        ?.trim()
        ?.takeIf {
            it.isNotEmpty()
        }
}


/**
 * Limpia un String obligatorio.
 *
 * Si viene vacío es un error porque IDs como
 * session_id no pueden quedar vacíos.
 */
private fun String.cleanRequired(): String {

    val cleanValue = trim()

    require(cleanValue.isNotEmpty()) {
        "El identificador NDVI no puede estar vacío."
    }

    return cleanValue
}


/**
 * Evita guardar:
 *
 * NaN
 * Infinity
 * -Infinity
 *
 * dentro de Room.
 */
private fun Double?.finiteOrNull(): Double? {

    return this?.takeIf {
        it.isFinite()
    }
}


/**
 * Convierte JsonElement a texto para guardarlo
 * dentro de SQLite.
 *
 * null / JsonNull -> null
 *
 * objeto/lista -> JSON String
 */
private fun JsonElement?.toStoredJson(): String? {

    if (this == null || isJsonNull) {
        return null
    }

    return toString()
}
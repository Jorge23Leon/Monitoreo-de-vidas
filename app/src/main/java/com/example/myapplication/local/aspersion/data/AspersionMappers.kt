package com.example.myapplication.local.aspersion.data

import com.example.myapplication.local.aspersion.data.remote.AspersionPointDto
import com.example.myapplication.local.aspersion.data.remote.AspersionSessionDto
import com.example.myapplication.local.aspersion.data.remote.AspersionStatsDto
import com.example.myapplication.local.aspersion.data.remote.AspersionVariableDto
import com.example.myapplication.local.entities.LocalAspersionPointEntity
import com.example.myapplication.local.entities.LocalAspersionSessionEntity
import com.example.myapplication.local.entities.LocalAspersionStatsEntity
import com.example.myapplication.local.entities.LocalAspersionVariableStatEntity
import com.google.gson.JsonElement

internal fun AspersionSessionDto.toLocalEntity(
    existing: LocalAspersionSessionEntity? = null
): LocalAspersionSessionEntity {
    val cleanId = id.trim()
    require(cleanId.isNotEmpty()) {
        "El servidor devolvió una sesión de aspersión sin UUID."
    }

    val remotePointsCount = pointsCount?.coerceAtLeast(0)
    val downloadedPointsCount = existing?.downloadedPointsCount ?: 0
    val stillComplete = existing?.pointsDownloadComplete == true &&
        (remotePointsCount == null || remotePointsCount == downloadedPointsCount)

    return LocalAspersionSessionEntity(
        sessionId = cleanId,
        programId = program.cleanNullable(),
        programName = context?.program?.name.cleanNullable()
            ?: existing?.programName,
        programCycle = context?.program?.cycle.cleanNullable()
            ?: existing?.programCycle,
        plotId = plot.cleanNullable(),
        plotName = context?.plot?.name.cleanNullable()
            ?: existing?.plotName,
        plotCode = context?.plot?.code.cleanNullable()
            ?: existing?.plotCode,
        ranchId = context?.ranch?.id.cleanNullable()
            ?: existing?.ranchId,
        ranchName = context?.ranch?.name.cleanNullable()
            ?: existing?.ranchName,
        producerId = context?.producer?.id.cleanNullable()
            ?: existing?.producerId,
        producerName = context?.producer?.name.cleanNullable()
            ?: existing?.producerName,
        dataCentralIds = context
            ?.dataCentrals
            ?.mapNotNull { item -> item.id.cleanNullable() }
            ?.distinct()
            ?.sorted()
            ?.joinToString(",")
            ?.takeIf(String::isNotEmpty)
            ?: existing?.dataCentralIds,
        evaluationId = evaluation.cleanNullable(),
        aspersionDate = aspersionDate.cleanNullable(),
        status = status.cleanNullable(),
        assignedToId = assignedTo?.id.cleanNullable(),
        assignedToUsername = assignedTo?.username.cleanNullable(),
        estStartDate = estStartDate.cleanNullable(),
        estFinishDate = estFinishDate.cleanNullable(),
        actStartDate = actStartDate.cleanNullable(),
        actFinishDate = actFinishDate.cleanNullable(),
        importStatus = importStatus.cleanNullable(),
        importErrorsJson = importErrors.toStoredJson(),
        importedAt = importedAt.cleanNullable(),
        lastTemplateId = lastTemplate.cleanNullable(),
        pointsCount = remotePointsCount,
        createdAt = createdAt.cleanNullable(),
        updatedAt = updatedAt.cleanNullable(),
        downloadedPointsCount = downloadedPointsCount,
        pointsDownloadComplete = stillComplete,
        lastSyncedAt = existing?.lastSyncedAt
    )
}

/**
 * GeoJSON usa el orden [longitud, latitud]. Un punto inválido cancela la
 * sincronización completa para conservar intacta la copia local anterior.
 */
internal fun AspersionPointDto.toLocalEntityOrNull(
    expectedSessionId: String
): LocalAspersionPointEntity? {
    val cleanPointId = id.trim()
    val cleanExpectedSessionId = expectedSessionId.trim()
    if (cleanPointId.isEmpty() || cleanExpectedSessionId.isEmpty()) return null

    val dtoSessionId = sessionHeader.cleanNullable()
    if (dtoSessionId != null && dtoSessionId != cleanExpectedSessionId) return null

    val longitude = geom
        ?.coordinates
        ?.getOrNull(0)
        ?.takeIf { it.isFinite() && it in -180.0..180.0 }
        ?: return null
    val latitude = geom
        ?.coordinates
        ?.getOrNull(1)
        ?.takeIf { it.isFinite() && it in -90.0..90.0 }
        ?: return null

    return LocalAspersionPointEntity(
        pointId = cleanPointId,
        sessionId = cleanExpectedSessionId,
        plotId = plot.cleanNullable(),
        geomType = geom?.type.cleanNullable(),
        longitude = longitude,
        latitude = latitude,
        timestamp = timestamp.cleanNullable(),
        passNumber = passNumber,
        elevationM = elevationM.toFiniteDoubleOrNull(),
        courseDeg = courseDeg.toFiniteDoubleOrNull(),
        vehicleHeading = vehicleHeading.toFiniteDoubleOrNull(),
        distanceM = distanceM.toFiniteDoubleOrNull(),
        durationS = durationS,
        speedKmh = speedKmh.toFiniteDoubleOrNull(),
        satellites = satellites,
        gnssHdop = gnssHdop.toFiniteDoubleOrNull(),
        gnssVdop = gnssVdop.toFiniteDoubleOrNull(),
        gnssPdop = gnssPdop.toFiniteDoubleOrNull(),
        isDiffActive = isDiffActive,
        diffMode = diffMode.cleanNullable(),
        xteImplement = xteImplement.toFiniteDoubleOrNull(),
        xteVehicle = xteVehicle.toFiniteDoubleOrNull(),
        isSteeringActive = isSteeringActive,
        isAreaCounting = isAreaCounting,
        activeRows = activeRows,
        appStatus = appStatus.cleanNullable(),
        boomWidthM = boomWidthM.toFiniteDoubleOrNull(),
        liquidFlowLs = liquidFlowLs.toFiniteDoubleOrNull(),
        boomPressureBar = boomPressureBar.toFiniteDoubleOrNull(),
        pressAgKpa = pressAgKpa.toFiniteDoubleOrNull(),
        pressAuxKpa = pressAuxKpa.toFiniteDoubleOrNull(),
        productionHah = productionHah.toFiniteDoubleOrNull(),
        dropletSize = dropletSize.cleanNullable(),
        nozzleColor = nozzleColor.cleanNullable(),
        nozzleCapacity = nozzleCapacity.toFiniteDoubleOrNull(),
        nozzleRefPressure = nozzleRefPressure.toFiniteDoubleOrNull(),
        targetRateL = targetRateL.toFiniteDoubleOrNull(),
        appliedRateL = appliedRateL.toFiniteDoubleOrNull(),
        productQuantity = productQuantity.toFiniteDoubleOrNull(),
        areaHa = areaHa.toFiniteDoubleOrNull(),
        rateQuality = rateQuality.cleanNullable(),
        evaluationId = evaluation.cleanNullable(),
        extraRawJson = extraRaw.toStoredJson(),
        createdAt = createdAt.cleanNullable()
    )
}

internal fun AspersionStatsDto.toLocalEntity(
    expectedSessionId: String,
    cachedAt: Long
): LocalAspersionStatsEntity {
    val cleanExpectedSessionId = expectedSessionId.trim()
    require(sessionHeader.trim() == cleanExpectedSessionId) {
        "Las estadísticas no pertenecen a la sesión solicitada."
    }

    return LocalAspersionStatsEntity(
        sessionId = cleanExpectedSessionId,
        plotId = plotId.cleanNullable(),
        pointsCount = pointsCount?.coerceAtLeast(0),
        areaTotalHa = areaTotalHa.toFiniteDoubleOrNull(),
        meanTargetL = meanTargetL.toFiniteDoubleOrNull(),
        meanAppliedL = meanAppliedL.toFiniteDoubleOrNull(),
        ratioApplied = ratioApplied.toFiniteDoubleOrNull(),
        pctBelow = pctBelow.toFiniteDoubleOrNull(),
        pctInRange = pctInRange.toFiniteDoubleOrNull(),
        pctAbove = pctAbove.toFiniteDoubleOrNull(),
        lastRefresh = lastRefresh.cleanNullable(),
        cachedAt = cachedAt
    )
}

internal fun AspersionVariableDto.toLocalEntityOrNull(
    sessionId: String,
    cachedAt: Long
): LocalAspersionVariableStatEntity? {
    val cleanSessionId = sessionId.trim()
    val cleanKey = key.trim()
    if (cleanSessionId.isEmpty() || cleanKey.isEmpty()) return null

    return LocalAspersionVariableStatEntity(
        sessionId = cleanSessionId,
        variableKey = cleanKey,
        label = label.trim().ifEmpty { cleanKey },
        count = count.coerceAtLeast(0),
        meanValue = mean?.takeIf { it.isFinite() },
        minValue = min?.takeIf { it.isFinite() },
        maxValue = max?.takeIf { it.isFinite() },
        stddev = stddev?.takeIf { it.isFinite() },
        cachedAt = cachedAt
    )
}

private fun String?.cleanNullable(): String? =
    this?.trim()?.takeIf(String::isNotEmpty)

private fun String?.toFiniteDoubleOrNull(): Double? =
    cleanNullable()
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() }

private fun JsonElement?.toStoredJson(): String? =
    this
        ?.takeUnless(JsonElement::isJsonNull)
        ?.toString()
        ?.trim()
        ?.takeIf(String::isNotEmpty)

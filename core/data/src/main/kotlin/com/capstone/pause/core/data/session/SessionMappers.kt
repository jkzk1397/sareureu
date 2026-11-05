package com.capstone.pause.core.data.session

import com.capstone.pause.core.database.entity.NudgeEventEntity
import com.capstone.pause.core.database.entity.SegmentEntity
import com.capstone.pause.core.database.entity.SessionEntity
import com.capstone.pause.core.database.entity.SessionWithSegments
import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.AppSegment
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ExitPoint
import com.capstone.pause.core.model.InteractionStats
import com.capstone.pause.core.model.NudgeEvent
import com.capstone.pause.core.model.ShortformSession

private const val UNLOCK = "UNLOCK"
private const val HOME = "HOME"
private const val FROM_APP = "FROM_APP"
private const val FROM_SHORTFORM = "FROM_SHORTFORM"
private const val UNKNOWN = "UNKNOWN"
private const val SCREEN_OFF = "SCREEN_OFF"
private const val TO_APP = "TO_APP"
private const val AFTER_NUDGE_STOP = "AFTER_NUDGE_STOP"

private fun kindOf(value: String?): AppKind = AppKind.entries.firstOrNull { it.name == value } ?: AppKind.OTHER

internal fun EntryPoint.encode(): Pair<String, String?> = when (this) {
    EntryPoint.Unlock -> UNLOCK to null
    EntryPoint.Home -> HOME to null
    is EntryPoint.FromApp -> FROM_APP to kind.name
    is EntryPoint.FromShortform -> FROM_SHORTFORM to packageName
    EntryPoint.Unknown -> UNKNOWN to null
}

internal fun decodeEntry(type: String, value: String?): EntryPoint = when (type) {
    UNLOCK -> EntryPoint.Unlock
    HOME -> EntryPoint.Home
    FROM_APP -> EntryPoint.FromApp(kindOf(value))
    FROM_SHORTFORM -> value?.let { EntryPoint.FromShortform(it) } ?: EntryPoint.Unknown
    else -> EntryPoint.Unknown
}

internal fun ExitPoint.encode(): Pair<String, String?> = when (this) {
    ExitPoint.ScreenOff -> SCREEN_OFF to null
    ExitPoint.Home -> HOME to null
    is ExitPoint.ToApp -> TO_APP to kind.name
    ExitPoint.AfterNudgeStop -> AFTER_NUDGE_STOP to null
    ExitPoint.Unknown -> UNKNOWN to null
}

internal fun decodeExit(type: String?, value: String?): ExitPoint? = when (type) {
    null -> null
    SCREEN_OFF -> ExitPoint.ScreenOff
    HOME -> ExitPoint.Home
    TO_APP -> ExitPoint.ToApp(kindOf(value))
    AFTER_NUDGE_STOP -> ExitPoint.AfterNudgeStop
    else -> ExitPoint.Unknown
}

internal fun ShortformSession.toEntity(): SessionEntity {
    val (entryType, entryValue) = entry.encode()
    val exitPair = exit?.encode()
    return SessionEntity(
        id = id,
        start = start,
        end = end,
        context = context,
        contextConfidence = contextConfidence,
        contextConfirmed = contextConfirmed,
        entryType = entryType,
        entryValue = entryValue,
        exitType = exitPair?.first,
        exitValue = exitPair?.second,
        swipes = interaction?.swipes,
        medianSwipeIntervalSec = interaction?.medianSwipeIntervalSec,
        likes = interaction?.likes,
        otherClicks = interaction?.otherClicks,
        maxFrictionLevel = maxFrictionLevel,
        extensions = extensions,
        stoppedByNudge = stoppedByNudge,
        checkIn = checkIn,
        source = source,
    )
}

internal fun ShortformSession.segmentEntities(): List<SegmentEntity> = segments.map {
    SegmentEntity(sessionId = id, packageName = it.packageName, surface = it.surface, start = it.start, end = it.end)
}

internal fun SessionWithSegments.toModel(): ShortformSession {
    val s = session
    val interaction = if (s.swipes != null) {
        InteractionStats(s.swipes ?: 0, s.medianSwipeIntervalSec ?: 0.0, s.likes ?: 0, s.otherClicks ?: 0)
    } else {
        null
    }
    return ShortformSession(
        id = s.id,
        start = s.start,
        end = s.end,
        segments = segments.sortedBy { it.start }.map { AppSegment(it.packageName, it.surface, it.start, it.end) },
        context = s.context,
        contextConfidence = s.contextConfidence,
        contextConfirmed = s.contextConfirmed,
        entry = decodeEntry(s.entryType, s.entryValue),
        exit = decodeExit(s.exitType, s.exitValue),
        interaction = interaction,
        maxFrictionLevel = s.maxFrictionLevel,
        extensions = s.extensions,
        stoppedByNudge = s.stoppedByNudge,
        checkIn = s.checkIn,
        source = s.source,
    )
}

internal fun NudgeEvent.toEntity(sessionId: Long, source: DataSource) = NudgeEventEntity(
    sessionId = sessionId,
    at = at,
    level = level,
    copyId = copyId,
    response = response,
    reasonChip = reasonChip,
    source = source,
)

internal fun NudgeEventEntity.toModel() = NudgeEvent(sessionId, at, level, copyId, response, reasonChip)

package com.capstone.pause.core.intervention.nudge

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.capstone.pause.core.intervention.di.MonitoringStore
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 개입 엔진 상태([NudgeSnapshot])를 기기 안(`monitoring` DataStore)에 둔다. 감지를 껐다 켜거나 앱이 강제 종료돼도
 * 오늘의 단계·연장 횟수·오늘은 그만이 풀리지 않게 하려는 것이다. 실제 감지에만 쓴다(시연은 매번 처음부터).
 * 화면 글자나 앱 이름은 담지 않는다(시각과 숫자뿐).
 */
@Singleton
class NudgeStateStore @Inject constructor(@MonitoringStore private val store: DataStore<Preferences>) {
    /** 저장된 상태. 없거나 깨졌으면 null. */
    suspend fun load(): NudgeSnapshot? = runCatching { decode(store.data.first()) }.getOrNull()

    /** 상태 저장(덮어쓰기). */
    suspend fun save(snapshot: NudgeSnapshot) {
        store.edit { encode(it, snapshot) }
    }

    /** 지우기(모든 기록 삭제). */
    suspend fun clear() {
        store.edit { prefs -> ALL.forEach { prefs.remove(it) } }
    }

    private fun decode(prefs: Preferences): NudgeSnapshot? {
        val day = prefs[DAY]?.let(LocalDate::parse) ?: return null
        val start = prefs[SESSION_START]
        val session = if (start == null) {
            null
        } else {
            SessionSnapshot(
                start = Instant.ofEpochMilli(start),
                elapsedSec = prefs[ELAPSED] ?: 0,
                level = prefs[LEVEL] ?: 0,
                dueSec = prefs[DUE] ?: 0,
                windowStartSec = prefs[WINDOW_START] ?: 0,
                cardLevel = prefs[CARD_LEVEL],
                messaged = prefs[MESSAGED] ?: false,
                savedAt = Instant.ofEpochMilli(prefs[SAVED_AT] ?: start),
            )
        }
        return NudgeSnapshot(day, prefs[EXTENSIONS] ?: 0, prefs[SNOOZE_UNTIL]?.let(Instant::ofEpochMilli), session)
    }

    private fun encode(prefs: MutablePreferences, s: NudgeSnapshot) {
        ALL.forEach { prefs.remove(it) }
        prefs[DAY] = s.day.toString()
        prefs[EXTENSIONS] = s.extensionsToday
        s.snoozeUntil?.let { prefs[SNOOZE_UNTIL] = it.toEpochMilli() }
        val session = s.session ?: return
        prefs[SESSION_START] = session.start.toEpochMilli()
        prefs[ELAPSED] = session.elapsedSec
        prefs[LEVEL] = session.level
        prefs[DUE] = session.dueSec
        prefs[WINDOW_START] = session.windowStartSec
        session.cardLevel?.let { prefs[CARD_LEVEL] = it }
        prefs[MESSAGED] = session.messaged
        prefs[SAVED_AT] = session.savedAt.toEpochMilli()
    }

    private companion object {
        val DAY = stringPreferencesKey("nudge_day")
        val EXTENSIONS = intPreferencesKey("nudge_extensions")
        val SNOOZE_UNTIL = longPreferencesKey("nudge_snooze_until")
        val SESSION_START = longPreferencesKey("nudge_session_start")
        val ELAPSED = longPreferencesKey("nudge_session_elapsed")
        val LEVEL = intPreferencesKey("nudge_session_level")
        val DUE = longPreferencesKey("nudge_session_due")
        val WINDOW_START = longPreferencesKey("nudge_session_window_start")
        val CARD_LEVEL = intPreferencesKey("nudge_session_card_level")
        val MESSAGED = booleanPreferencesKey("nudge_session_messaged")
        val SAVED_AT = longPreferencesKey("nudge_session_saved_at")
        val ALL: List<Preferences.Key<*>> = listOf(
            DAY,
            EXTENSIONS,
            SNOOZE_UNTIL,
            SESSION_START,
            ELAPSED,
            LEVEL,
            DUE,
            WINDOW_START,
            CARD_LEVEL,
            MESSAGED,
            SAVED_AT,
        )
    }
}

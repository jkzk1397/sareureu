package com.capstone.pause.core.data.monitoring

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.capstone.pause.core.data.di.SettingsStore
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 감지를 꺼 둔 구간을 `settings` DataStore에 둔다(모든 기록 삭제 때 같이 지워진다).
 * 세션 보관 기간(90일)보다 조금 긴 [KEEP]만 남기고, 구간 수도 [MAX_GAPS]까지만 둔다.
 */
@Singleton
internal class DataStoreMonitoringGapRepository @Inject constructor(
    @SettingsStore private val store: DataStore<Preferences>,
) : MonitoringGapRepository {

    override val gaps: Flow<List<MonitoringGap>> = store.data.map { MonitoringGapCodec.decode(it[KEY]) }
        .distinctUntilChanged()

    override suspend fun recordOff(at: Instant) {
        store.edit { prefs ->
            val now = MonitoringGapCodec.decode(prefs[KEY])
            if (now.lastOrNull()?.onAt == null && now.isNotEmpty()) return@edit
            prefs[KEY] = MonitoringGapCodec.encode(MonitoringGapCodec.trim(now + MonitoringGap(at, null), at))
        }
    }

    override suspend fun recordOn(at: Instant) {
        store.edit { prefs ->
            val now = MonitoringGapCodec.decode(prefs[KEY])
            val open = now.lastOrNull()?.takeIf { it.onAt == null } ?: return@edit
            val closed = open.copy(onAt = maxOf(at, open.offAt))
            prefs[KEY] = MonitoringGapCodec.encode(MonitoringGapCodec.trim(now.dropLast(1) + closed, at))
        }
    }

    override suspend fun clear() {
        store.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("monitoring_gaps")
    }
}

/** 구간 목록 ↔ 문자열("끈ms:켠ms,끈ms:" …). 순수. */
internal object MonitoringGapCodec {
    val KEEP: Duration = Duration.ofDays(120)
    const val MAX_GAPS = 400

    fun encode(gaps: List<MonitoringGap>): String =
        gaps.joinToString(",") { "${it.offAt.toEpochMilli()}:${it.onAt?.toEpochMilli() ?: ""}" }

    fun decode(text: String?): List<MonitoringGap> = text.orEmpty().split(',').mapNotNull { part ->
        val bits = part.split(':')
        val off = bits.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        MonitoringGap(Instant.ofEpochMilli(off), bits.getOrNull(1)?.toLongOrNull()?.let(Instant::ofEpochMilli))
    }

    /** 오래된 구간과 넘치는 구간을 버린다. */
    fun trim(gaps: List<MonitoringGap>, now: Instant): List<MonitoringGap> =
        gaps.filter { (it.onAt ?: now).isAfter(now.minus(KEEP)) }.takeLast(MAX_GAPS)
}

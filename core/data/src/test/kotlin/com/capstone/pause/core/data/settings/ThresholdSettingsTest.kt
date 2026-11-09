package com.capstone.pause.core.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.capstone.pause.core.model.ContextSlot
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThresholdSettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun defaults_nightIsTenRestFifteen() {
        assertEquals(10, DefaultThresholds.ALL.getValue(ContextSlot.PRE_SLEEP))
        assertEquals(10, DefaultThresholds.ALL.getValue(ContextSlot.LATE_NIGHT))
        assertEquals(15, DefaultThresholds.ALL.getValue(ContextSlot.COMMUTE))
    }

    @Test
    fun groups_coverAllSlotsOnce() {
        val normal = DefaultThresholds.slotsOf(ThresholdGroup.NORMAL)
        val night = DefaultThresholds.slotsOf(ThresholdGroup.NIGHT)
        assertEquals(6, normal.size)
        assertEquals(setOf(ContextSlot.PRE_SLEEP, ContextSlot.LATE_NIGHT), night)
        assertEquals(ContextSlot.entries.toSet(), normal + night)
        assertEquals(ThresholdGroup.NIGHT, DefaultThresholds.groupOf(ContextSlot.LATE_NIGHT))
        assertEquals(ThresholdGroup.NORMAL, DefaultThresholds.groupOf(ContextSlot.MEAL))
    }

    @Test
    fun groupValue_isMinimumInGroup() {
        val mixed = DefaultThresholds.ALL + (ContextSlot.MEAL to 5) + (ContextSlot.PRE_SLEEP to 20)
        assertEquals(5, DefaultThresholds.groupValue(mixed, ThresholdGroup.NORMAL))
        assertEquals(10, DefaultThresholds.groupValue(mixed, ThresholdGroup.NIGHT))
    }

    @Test
    fun setNormalSeven_changesSixSlotsOnly() = runTest {
        val repo = repo()
        repo.setThresholds(DefaultThresholds.slotsOf(ThresholdGroup.NORMAL), 7)
        val t = repo.settings.first().thresholds
        DefaultThresholds.slotsOf(ThresholdGroup.NORMAL).forEach { assertEquals(7, t.getValue(it)) }
        assertEquals(10, t.getValue(ContextSlot.PRE_SLEEP))
        assertEquals(10, t.getValue(ContextSlot.LATE_NIGHT))
    }

    @Test
    fun outOfRange_rejected() = runTest {
        val repo = repo()
        assertFailsWith<IllegalArgumentException> { repo.setThresholds(DefaultThresholds.NIGHT_SLOTS, 0) }
        assertFailsWith<IllegalArgumentException> { repo.setThreshold(ContextSlot.MEAL, 61) }
        repo.setThreshold(ContextSlot.MEAL, 60)
        repo.setThreshold(ContextSlot.BREAK, 1)
        val t = repo.settings.first().thresholds
        assertEquals(60, t.getValue(ContextSlot.MEAL))
        assertEquals(1, t.getValue(ContextSlot.BREAK))
    }

    private fun kotlinx.coroutines.test.TestScope.repo() = DataStoreSettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tmp.newFile("s.preferences_pb").also { it.delete() }
        },
    )
}

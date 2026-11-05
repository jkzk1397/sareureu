package com.capstone.pause.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.capstone.pause.core.database.entity.SegmentEntity
import com.capstone.pause.core.database.entity.SessionEntity
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.Surface
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionDaoTest {
    private lateinit var db: PauseDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PauseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun session(start: Long, source: DataSource) = SessionEntity(
        start = Instant.ofEpochSecond(start), end = Instant.ofEpochSecond(start + 600),
        context = ContextSlot.PRE_SLEEP, contextConfidence = Confidence.HIGH, contextConfirmed = false,
        entryType = "FROM_APP", entryValue = "MESSENGER", exitType = "SCREEN_OFF", exitValue = null,
        swipes = null, medianSwipeIntervalSec = null, likes = null, otherClicks = null,
        maxFrictionLevel = 1, extensions = 0, stoppedByNudge = false, checkIn = null, source = source,
    )

    @Test
    fun insertWithSegmentsAndReadBySource() = runTest {
        val dao = db.sessionDao()
        val segment = SegmentEntity(
            sessionId = 0,
            packageName = "com.google.android.youtube",
            surface = Surface.APP_UNKNOWN,
            start = Instant.ofEpochSecond(1000),
            end = Instant.ofEpochSecond(1600),
        )
        dao.insertWithSegments(session(1000, DataSource.DEMO), listOf(segment))
        dao.insertWithSegments(session(1000, DataSource.REAL), emptyList())

        val demo = dao.observeRange(DataSource.DEMO, Instant.ofEpochSecond(0), Instant.ofEpochSecond(5000)).first()
        assertEquals(1, demo.size)
        assertEquals(1, demo.single().segments.size)

        dao.deleteBySource(DataSource.DEMO)
        assertEquals(0, dao.count(DataSource.DEMO))
        assertEquals(1, dao.count(DataSource.REAL))
    }
}

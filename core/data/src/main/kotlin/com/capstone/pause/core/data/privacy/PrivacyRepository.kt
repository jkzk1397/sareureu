package com.capstone.pause.core.data.privacy

import android.content.Context
import android.net.Uri
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.common.Dispatcher
import com.capstone.pause.core.common.PauseDispatchers
import com.capstone.pause.core.data.profile.ConsentRepository
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.database.dao.MessageDao
import com.capstone.pause.core.database.dao.NudgeEventDao
import com.capstone.pause.core.database.dao.PlanDao
import com.capstone.pause.core.database.dao.ReportDao
import com.capstone.pause.core.database.dao.SessionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 개인정보 기능: 내 기록 내보내기(JSON)와 모든 기록 삭제(04 문서 1절 4번, PRD 12절). */
interface PrivacyRepository {
    /** 이 폰에 저장된 내 기록 전부를 JSON으로. */
    suspend fun exportJson(): String

    /** 사용자가 고른 위치([uri], SAF `ACTION_CREATE_DOCUMENT`)에 JSON을 쓴다. 성공하면 true. */
    suspend fun exportTo(uri: Uri): Boolean

    /** 로컬 기록을 모두 지운다(Room 전체·DataStore·캐시). 실패한 단계 이름을 돌려준다(빈 목록 = 성공). */
    suspend fun eraseLocal(): List<String>
}

@Singleton
internal class DefaultPrivacyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val consentRepository: ConsentRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionDao: SessionDao,
    private val nudgeEventDao: NudgeEventDao,
    private val messageDao: MessageDao,
    private val planDao: PlanDao,
    private val reportDao: ReportDao,
    private val eraser: EraseOrchestrator,
    private val clock: AppClock,
    private val log: AppLog,
    @Dispatcher(PauseDispatchers.IO) private val io: CoroutineDispatcher,
) : PrivacyRepository {

    override suspend fun exportJson(): String = withContext(io) {
        ExportMapper.encode(
            ExportSnapshot(
                profile = profileRepository.profile.first(),
                consents = consentRepository.consents.first().values,
                settings = settingsRepository.settings.first(),
                sessions = sessionDao.getAll(),
                nudges = nudgeEventDao.getAll(),
                messages = messageDao.getAll(),
                plans = planDao.getAll(),
                hypotheses = reportDao.getAllHypotheses(),
            ),
            clock.now(),
        )
    }

    override suspend fun exportTo(uri: Uri): Boolean = withContext(io) {
        runCatching {
            val text = exportJson()
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: error("output stream null")
        }.onFailure { log.w(TAG, "export failed", it) }.isSuccess
    }

    override suspend fun eraseLocal(): List<String> = withContext(io) { eraser.run() }

    private companion object {
        const val TAG = "Privacy"
    }
}

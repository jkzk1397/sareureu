package com.capstone.pause.core.data.copy

import android.content.Context
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.common.Dispatcher
import com.capstone.pause.core.common.PauseDispatchers
import com.capstone.pause.core.database.dao.CopyPackDao
import com.capstone.pause.core.model.CopyPack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * 문구 팩 저장소. 주간 리포트 때 AI가 만들어 검증을 통과한 팩(Room `copy_pack`)이 있으면 그것,
 * 없으면 기본 팩(`assets/copy/default_pack.json`). 개입 엔진은 여기서 고르기만 한다(ADR-0001).
 */
interface CopyPackRepository {
    /** 지금 쓰는 팩. */
    val activePack: Flow<CopyPack>

    /** 기본 팩(항상 있음). */
    suspend fun defaultPack(): CopyPack
}

/** 기본 팩과 AI 팩을 같은 파서로 읽는다(05 문서 5.2절). */
object CopyPackJson {
    /** 파서. */
    val json: Json = Json { ignoreUnknownKeys = true }

    /** JSON 문자열 → 팩. */
    fun parse(text: String): CopyPack = json.decodeFromString(CopyPack.serializer(), text)

    /** 기본 팩 자산 경로. */
    const val DEFAULT_ASSET = "copy/default_pack.json"
}

@Singleton
internal class DefaultCopyPackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val copyPackDao: CopyPackDao,
    private val log: AppLog,
    @Dispatcher(PauseDispatchers.IO) private val io: CoroutineDispatcher,
) : CopyPackRepository {

    @Volatile
    private var cachedDefault: CopyPack? = null

    override val activePack: Flow<CopyPack> = copyPackDao.observeActive().map { row ->
        row?.let { entity ->
            runCatching { CopyPackJson.parse(entity.json) }
                .onFailure { log.w(TAG, "active copy pack unreadable, using default", it) }
                .getOrNull()
        } ?: defaultPack()
    }.flowOn(io)

    override suspend fun defaultPack(): CopyPack = cachedDefault ?: context.assets.open(CopyPackJson.DEFAULT_ASSET)
        .bufferedReader()
        .use { CopyPackJson.parse(it.readText()) }
        .also { cachedDefault = it }

    private companion object {
        const val TAG = "CopyPackRepository"
    }
}

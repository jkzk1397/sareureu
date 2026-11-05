package com.capstone.pause.core.data.plan

import com.capstone.pause.core.data.session.activeSource
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.database.dao.PlanDao
import com.capstone.pause.core.database.dao.PokeDao
import com.capstone.pause.core.database.entity.PlanEntity
import com.capstone.pause.core.database.entity.PokeInboxEntity
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.IfThenPlan
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** 만약~하면 계획 저장소. 저장은 사용자가 버튼을 눌렀을 때만. */
interface PlanRepository {
    /** 켜 둔 계획(최신순). */
    fun observeActivePlans(): Flow<List<IfThenPlan>>

    /** 계획 저장. */
    suspend fun add(plan: IfThenPlan, source: DataSource): Long

    /** 출처별 지우기. */
    suspend fun deleteAll(source: DataSource)
}

/** 받은 잔소리(친구가 맡겨 둔 메시지). 내용은 숏폼 2단계에서만 꺼내 보여 준다. */
data class ReceivedPoke(
    val id: String,
    val fromId: String,
    val fromNickname: String,
    val text: String,
    val createdAt: Instant,
    val expiresAt: Instant,
)

/** 받은 잔소리 저장소(로컬 받은편지함). */
interface PokeRepository {
    /** 아직 쓰지 않은, 만료 전 잔소리. */
    fun observeUnused(now: Instant): Flow<List<ReceivedPoke>>

    /** 넣기. */
    suspend fun add(pokes: List<ReceivedPoke>, source: DataSource)

    /** 사용 표시. */
    suspend fun markUsed(id: String)

    /** 출처별 지우기. */
    suspend fun deleteAll(source: DataSource)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class RoomPlanRepository @Inject constructor(
    private val planDao: PlanDao,
    private val settingsRepository: SettingsRepository,
) : PlanRepository {
    override fun observeActivePlans(): Flow<List<IfThenPlan>> =
        settingsRepository.activeSource().flatMapLatest { source ->
            planDao.observeActive(source).map { rows ->
                rows.map { IfThenPlan(it.id, it.context, it.ifText, it.thenText, it.createdAt, it.active) }
            }
        }

    override suspend fun add(plan: IfThenPlan, source: DataSource): Long = planDao.insert(
        PlanEntity(
            context = plan.context,
            ifText = plan.ifText,
            thenText = plan.thenText,
            createdAt = plan.createdAt,
            active = plan.active,
            source = source,
        ),
    )

    override suspend fun deleteAll(source: DataSource) = planDao.deleteBySource(source)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class RoomPokeRepository @Inject constructor(
    private val pokeDao: PokeDao,
    private val settingsRepository: SettingsRepository,
) : PokeRepository {
    override fun observeUnused(now: Instant): Flow<List<ReceivedPoke>> =
        settingsRepository.activeSource().flatMapLatest { source ->
            pokeDao.observeUnused(source, now).map { rows ->
                rows.map { ReceivedPoke(it.id, it.fromId, it.fromNickname, it.text, it.createdAt, it.expiresAt) }
            }
        }

    override suspend fun add(pokes: List<ReceivedPoke>, source: DataSource) = pokeDao.insert(
        pokes.map {
            PokeInboxEntity(
                it.id,
                it.fromId,
                it.fromNickname,
                it.text,
                it.createdAt,
                it.expiresAt,
                used = false,
                source,
            )
        },
    )

    override suspend fun markUsed(id: String) = pokeDao.markUsed(id)

    override suspend fun deleteAll(source: DataSource) = pokeDao.deleteBySource(source)
}

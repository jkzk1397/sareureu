package com.capstone.pause.core.sensing.demo

import android.content.Context
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.Dispatcher
import com.capstone.pause.core.common.PauseDispatchers
import com.capstone.pause.core.data.demo.DemoDataSeeder
import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.data.plan.PlanRepository
import com.capstone.pause.core.data.plan.PokeRepository
import com.capstone.pause.core.data.plan.ReceivedPoke
import com.capstone.pause.core.data.report.WeeklyReportRepository
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.IfThenPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `assets/demo/friends.json` 모양. */
@Serializable
internal data class DemoFriendsFile(
    val friends: List<DemoFriend> = emptyList(),
    val pokes: List<DemoPoke> = emptyList(),
    val plans: List<DemoPlan> = emptyList(),
)

/** 가상 친구. */
@Serializable
internal data class DemoFriend(val id: String, val nickname: String, val reductionRate: Double, val coverage: Double)

/** 가상 친구가 맡긴 잔소리. */
@Serializable
internal data class DemoPoke(
    val id: String,
    val fromId: String,
    val fromNickname: String,
    val text: String,
    val validDays: Long,
)

/** 시연 계획. */
@Serializable
internal data class DemoPlan(val context: ContextSlot? = null, val ifText: String, val thenText: String)

/** [DemoScenario]와 `assets/demo/`로 시연 데이터를 넣는다. 모두 `source = DEMO`. */
@Singleton
internal class AssetDemoDataSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val planRepository: PlanRepository,
    private val pokeRepository: PokeRepository,
    private val weeklyReportRepository: WeeklyReportRepository,
    private val clock: AppClock,
    @Dispatcher(PauseDispatchers.IO) private val io: CoroutineDispatcher,
) : DemoDataSeeder {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun seed(pattern: DemoPattern) = withContext(io) {
        clear()
        val now = clock.now()
        DemoScenario.generate(pattern, clock.today(), clock.zone, now).forEach { plan ->
            sessionRepository.insert(plan.session, plan.nudges)
        }
        val file = context.assets.open(FRIENDS_ASSET).bufferedReader().use {
            json.decodeFromString<DemoFriendsFile>(it.readText())
        }
        pokeRepository.add(
            file.pokes.map {
                ReceivedPoke(
                    id = it.id,
                    fromId = it.fromId,
                    fromNickname = it.fromNickname,
                    text = it.text,
                    createdAt = now.minus(Duration.ofHours(POKE_AGE_HOURS)),
                    expiresAt = now.plus(Duration.ofDays(it.validDays)),
                )
            },
            DataSource.DEMO,
        )
        file.plans.forEach {
            planRepository.add(
                IfThenPlan(
                    0,
                    it.context,
                    it.ifText,
                    it.thenText,
                    now.minus(Duration.ofDays(PLAN_AGE_DAYS)),
                    active = true,
                ),
                DataSource.DEMO,
            )
        }
    }

    override suspend fun clear() = withContext(io) {
        sessionRepository.deleteAll(DataSource.DEMO)
        planRepository.deleteAll(DataSource.DEMO)
        pokeRepository.deleteAll(DataSource.DEMO)
        weeklyReportRepository.deleteAll(DataSource.DEMO)
    }

    private companion object {
        const val FRIENDS_ASSET = "demo/friends.json"
        const val POKE_AGE_HOURS = 5L
        const val PLAN_AGE_DAYS = 3L
    }
}

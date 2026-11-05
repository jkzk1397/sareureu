package com.capstone.pause.core.ai.di

import android.content.Context
import com.capstone.pause.core.ai.AiCallLogger
import com.capstone.pause.core.ai.AiRouter
import com.capstone.pause.core.ai.coach.CoachConversation
import com.capstone.pause.core.ai.coach.CoachSession
import com.capstone.pause.core.ai.coach.RepositoryCoachTools
import com.capstone.pause.core.ai.engine.coach.CoachEngine
import com.capstone.pause.core.ai.engine.coach.CoachTools
import com.capstone.pause.core.ai.engine.mock.MockLlmClient
import com.capstone.pause.core.ai.engine.prompt.PromptRegistry
import com.capstone.pause.core.ai.engine.run.AiRunner
import com.capstone.pause.core.ai.log.RoomAiCallLogger
import com.capstone.pause.core.ai.router.DefaultAiRouter
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.copy.CopyPackRepository
import com.capstone.pause.core.data.strategy.StrategyRepository
import com.capstone.pause.core.designsystem.R as DesignR
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** 캐릭터 이름(문구 자리표시자 `{buddy}`용). Kotlin에 브랜드 이름을 쓰지 않으려고 리소스에서 읽는다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BuddyName

/** AI 실행기·프롬프트·모의 제공자 제공. `report/`의 생성기도 이것을 주입받는다. */
@Module
@InstallIn(SingletonComponent::class)
internal object AiProvidesModule {
    @Provides
    @Singleton
    fun promptRegistry(): PromptRegistry = PromptRegistry()

    @Provides
    @Singleton
    fun javaClock(appClock: AppClock): Clock = appClock.clock

    @Provides
    @BuddyName
    fun buddyName(@ApplicationContext context: Context): String = context.getString(DesignR.string.buddy_name)

    /** 기본 문구 팩·전략은 작은 자산 파일이라 처음 주입할 때 한 번 IO로 읽는다. */
    @Provides
    @Singleton
    fun mockLlmClient(copyPackRepository: CopyPackRepository, strategyRepository: StrategyRepository): MockLlmClient =
        runBlocking(Dispatchers.IO) {
            MockLlmClient(copyPackRepository.defaultPack(), strategyRepository.strategies())
        }

    @Provides
    @Singleton
    fun aiRunner(
        registry: PromptRegistry,
        mock: MockLlmClient,
        copyPackRepository: CopyPackRepository,
        strategyRepository: StrategyRepository,
        clock: Clock,
        @BuddyName buddyName: String,
    ): AiRunner = runBlocking(Dispatchers.IO) {
        AiRunner(registry, mock, copyPackRepository.defaultPack(), strategyRepository.strategies(), clock, buddyName)
    }

    @Provides
    @Singleton
    fun coachEngine(
        tools: CoachTools,
        registry: PromptRegistry,
        mock: MockLlmClient,
        clock: Clock,
        @BuddyName buddyName: String,
    ): CoachEngine = CoachEngine(tools, registry, mock, clock, buddyName)
}

/** 인터페이스 바인딩. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AiBindsModule {
    @Binds
    abstract fun aiRouter(impl: DefaultAiRouter): AiRouter

    @Binds
    abstract fun aiCallLogger(impl: RoomAiCallLogger): AiCallLogger

    @Binds
    abstract fun coachTools(impl: RepositoryCoachTools): CoachTools

    @Binds
    abstract fun coachConversation(impl: CoachSession): CoachConversation
}

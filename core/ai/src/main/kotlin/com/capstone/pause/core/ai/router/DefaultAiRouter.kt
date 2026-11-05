package com.capstone.pause.core.ai.router

import android.app.ActivityManager
import com.capstone.pause.core.ai.AiRouter
import com.capstone.pause.core.ai.client.CloudLlmClient
import com.capstone.pause.core.ai.client.OnDeviceLlmClient
import com.capstone.pause.core.ai.engine.llm.AiFeature
import com.capstone.pause.core.ai.engine.llm.LlmClient
import com.capstone.pause.core.ai.engine.llm.Provider
import com.capstone.pause.core.ai.engine.mock.MockLlmClient
import com.capstone.pause.core.data.profile.ConsentItem
import com.capstone.pause.core.data.profile.ConsentRepository
import com.capstone.pause.core.data.settings.AiMode
import com.capstone.pause.core.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** 라우팅 판단(순수 함수). 테스트로 표 전체를 확인한다. */
object AiRouting {
    /**
     * 쓸 제공자. 끔·동의 없음·앱이 화면에 없음이면 모의. 고른 방식이 이 기기에서 안 되면 모의(다른 실제 제공자로 바꾸지 않음:
     * 기기 안을 고른 사람의 입력을 클라우드로 보내면 안 되기 때문).
     */
    fun decide(
        mode: AiMode,
        consented: Boolean,
        cloudAvailable: Boolean,
        onDeviceAvailable: Boolean,
        foreground: Boolean,
    ): Provider = when {
        !foreground || !consented -> Provider.MOCK
        mode == AiMode.CLOUD && cloudAvailable -> Provider.CLOUD
        mode == AiMode.ON_DEVICE && onDeviceAvailable -> Provider.ON_DEVICE
        else -> Provider.MOCK
    }
}

/** 설정의 AI 방식 + AI 사용 동의 + 가용성 + 앱이 화면에 있는지로 클라이언트를 고른다. */
@Singleton
internal class DefaultAiRouter @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val consentRepository: ConsentRepository,
    private val cloud: CloudLlmClient,
    private val onDevice: OnDeviceLlmClient,
    private val mock: MockLlmClient,
) : AiRouter {

    override suspend fun clientFor(feature: AiFeature): LlmClient {
        val provider = AiRouting.decide(
            mode = settingsRepository.settings.first().aiMode,
            consented = consentRepository.consents.first()[ConsentItem.AI_USE]?.granted == true,
            cloudAvailable = cloud.isAvailable,
            onDeviceAvailable = onDevice.isAvailable,
            foreground = isForeground(),
        )
        return when (provider) {
            Provider.CLOUD -> cloud
            Provider.ON_DEVICE -> onDevice
            else -> mock
        }
    }

    override val usingRealAi: Flow<Boolean> =
        combine(settingsRepository.settings, consentRepository.consents) { settings, consents ->
            AiRouting.decide(
                mode = settings.aiMode,
                consented = consents[ConsentItem.AI_USE]?.granted == true,
                cloudAvailable = cloud.isAvailable,
                onDeviceAvailable = onDevice.isAvailable,
                foreground = true,
            ) != Provider.MOCK
        }

    /** AI는 앱이 화면에 있을 때만 부른다(AGENTS.md 7절). 백그라운드에서 불리면 모의로. */
    private fun isForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }
}

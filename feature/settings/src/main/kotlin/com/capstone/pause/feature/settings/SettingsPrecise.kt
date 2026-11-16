package com.capstone.pause.feature.settings

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.data.profile.ConsentItem
import com.capstone.pause.core.data.profile.ConsentRepository
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.component.pauseSurface
import com.capstone.pause.core.designsystem.theme.PauseTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/*
 * 정밀 모드(ADR-0004 보완): 스위치 → 따로 떼어 둔 고지 화면 → 동의하면 시스템 접근성 설정을 연다.
 * 실제로 쓰이는 조건은 '동의' + '시스템에서 켜짐'. 하나라도 아니면 앱 단위로 계속 센다.
 */

/** 고지 문구 버전. 문구를 바꾸면 올리고 다시 묻는다. */
internal const val PRECISE_CONSENT_VERSION = 1

/** 정밀 모드 상태. */
data class PreciseModeState(
    /** 고지에 동의했는지(스위치 위치). */
    val consented: Boolean = false,
    /** 시스템 접근성 설정에서 서비스가 켜져 있는지. */
    val serviceOn: Boolean = false,
    /** 고지 화면을 보여 줄지. */
    val showDisclosure: Boolean = false,
    /** 시스템 접근성 설정을 열어야 하는지(한 번 열고 끈다). */
    val openSystemSettings: Boolean = false,
) {
    /** 지금 쇼츠·릴스 화면만 세고 있는지. */
    val active: Boolean get() = consented && serviceOn
}

/** 정밀 모드 동작. */
sealed interface PreciseAction {
    /** 스위치. 켜면 고지 화면부터, 끄면 동의 철회. */
    data class Toggle(val enabled: Boolean) : PreciseAction

    /** 고지 화면의 답. */
    data class Answer(val agree: Boolean) : PreciseAction

    /** 시스템 설정을 열었다. */
    data object SettingsOpened : PreciseAction

    /** 시스템 설정을 다시 연다(동의했는데 아직 꺼져 있을 때). */
    data object OpenSettings : PreciseAction

    /** 화면으로 돌아왔다: 시스템에서 켰는지 다시 본다. */
    data object Refresh : PreciseAction
}

/** 시스템에서 이 앱의 접근성 서비스가 켜져 있는지. 읽기만 한다(켜고 끄는 것은 사용자가 시스템 설정에서). */
interface AccessibilityStatus {
    /** 켜져 있으면 true. */
    fun isOn(): Boolean
}

/** [Settings.Secure]의 켜진 접근성 서비스 목록에서 이 앱을 찾는다. */
class SystemAccessibilityStatus @Inject constructor(@ApplicationContext private val context: Context) :
    AccessibilityStatus {
    override fun isOn(): Boolean = runCatching {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        enabled.orEmpty().split(':').any { it.startsWith(context.packageName + "/") }
    }.getOrDefault(false)
}

/** 정밀 모드 화면 상태. */
@HiltViewModel
class PreciseModeViewModel @Inject constructor(
    private val consents: ConsentRepository,
    private val status: AccessibilityStatus,
) : ViewModel() {
    private val serviceOn = MutableStateFlow(status.isOn())
    private val disclosure = MutableStateFlow(false)
    private val openSettings = MutableStateFlow(false)

    /** 화면 상태. */
    val uiState: StateFlow<PreciseModeState> = combine(
        consents.consents.map { it[ConsentItem.ACCESSIBILITY]?.granted == true },
        serviceOn,
        disclosure,
        openSettings,
    ) { consented, on, show, open -> PreciseModeState(consented, on, show, open) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PreciseModeState())

    /** 동작 처리. 동의 기록은 버튼을 눌렀을 때만 남긴다(뒤로 가기는 동의가 아님). */
    fun onAction(action: PreciseAction) {
        when (action) {
            is PreciseAction.Toggle -> if (action.enabled) {
                disclosure.value = true
            } else {
                viewModelScope.launch { consents.record(ConsentItem.ACCESSIBILITY, PRECISE_CONSENT_VERSION, false) }
            }

            is PreciseAction.Answer -> {
                disclosure.value = false
                viewModelScope.launch {
                    consents.record(ConsentItem.ACCESSIBILITY, PRECISE_CONSENT_VERSION, action.agree)
                    if (action.agree && !status.isOn()) openSettings.value = true
                }
            }

            PreciseAction.OpenSettings -> openSettings.value = true

            PreciseAction.SettingsOpened -> openSettings.value = false

            PreciseAction.Refresh -> serviceOn.value = status.isOn()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** 설정 › 감지 › 감지 방식: 앱 단위는 늘 켜져 있고, 정밀 모드는 스위치. */
@Composable
internal fun DetectionModeRows(state: PreciseModeState, onAction: (PreciseAction) -> Unit) {
    SubTitle(stringResource(R.string.settings_detection_title))
    SwitchRow(
        label = stringResource(R.string.settings_detection_precise),
        description = stringResource(
            when {
                state.active -> R.string.settings_precise_on
                state.consented -> R.string.settings_precise_waiting
                else -> R.string.settings_detection_precise_short
            },
        ),
        checked = state.consented,
        onCheckedChange = { onAction(PreciseAction.Toggle(it)) },
    )
    if (state.consented && !state.serviceOn) {
        TextButton(
            onClick = { onAction(PreciseAction.OpenSettings) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(stringResource(R.string.settings_precise_open), color = PauseTheme.colors.ink)
        }
    }
    MoreNote(
        key = "detection",
        summary = stringResource(R.string.settings_detection_more),
        detail = stringResource(R.string.settings_detection_app_desc) + PARAGRAPH +
            stringResource(R.string.settings_detection_precise_desc),
    )
    if (state.showDisclosure) PreciseDisclosure(onAnswer = { onAction(PreciseAction.Answer(it)) })
}

/**
 * 정밀 모드 고지(ADR-0004 보완 문구 그대로). 다른 고지와 묶지 않는 별도 화면이고, 버튼을 눌러야만 동의가 남는다.
 * 바깥을 눌러 닫거나 뒤로 가면 거절로 본다.
 */
@Composable
internal fun PreciseDisclosure(onAnswer: (Boolean) -> Unit) {
    Dialog(
        onDismissRequest = { onAnswer(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val spacing = PauseTheme.spacing
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier
                .padding(spacing.md)
                .pauseSurface(PauseTheme.shapes.card)
                .verticalScroll(rememberScrollState())
                .padding(spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.settings_precise_disclosure_title),
                style = PauseTheme.type.headline,
                color = PauseTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            listOf(
                R.string.settings_precise_disclosure_what,
                R.string.settings_precise_disclosure_keep,
                R.string.settings_precise_disclosure_not_keep,
                R.string.settings_precise_disclosure_no_action,
                R.string.settings_precise_disclosure_decline,
            ).forEach { Text(stringResource(it), style = PauseTheme.type.body, color = PauseTheme.colors.ink) }
            PrimaryButton(text = stringResource(R.string.settings_precise_agree), onClick = { onAnswer(true) })
            TextButton(
                onClick = { onAnswer(false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouch),
            ) {
                Text(stringResource(R.string.settings_precise_decline), color = PauseTheme.colors.ink2)
            }
        }
    }
}

private const val PARAGRAPH = "\n\n"

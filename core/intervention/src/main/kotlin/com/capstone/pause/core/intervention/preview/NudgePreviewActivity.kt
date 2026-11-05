package com.capstone.pause.core.intervention.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.intervention.R
import com.capstone.pause.core.intervention.nudge.NudgeCommand
import com.capstone.pause.core.intervention.overlay.NudgeCard
import com.capstone.pause.core.intervention.overlay.OverlayController
import com.capstone.pause.core.intervention.overlay.OverlayDimens
import com.capstone.pause.core.intervention.overlay.PraiseCard
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 다른 앱 위 표시 권한이 없을 때 카드를 앱 화면으로 보여 준다(ONE_SHOT 3단계 시연 항목).
 * 동작은 [OverlayController]의 흐름으로 보내고 닫는다. '그만'의 홈 이동은 서비스가 한다.
 */
@AndroidEntryPoint
class NudgePreviewActivity : ComponentActivity() {
    @Inject
    lateinit var controller: OverlayController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PauseTheme {
                val command by controller.current.collectAsStateWithLifecycle()
                LaunchedEffect(command) { if (command == null) finish() }
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        PauseTheme.spacing.md,
                        androidx.compose.ui.Alignment.Bottom,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PauseTheme.colors.scrim)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(OverlayDimens.cardOuterPadding),
                ) {
                    // 반투명 바탕 위 작은 흰 글자는 대비가 낮아서, 표면색 알약 위에 잉크 글자로 둔다.
                    Text(
                        text = stringResource(R.string.intervention_preview_title),
                        style = PauseTheme.type.label,
                        color = PauseTheme.colors.ink,
                        modifier = Modifier
                            .background(PauseTheme.colors.surface, PauseTheme.shapes.pill)
                            .padding(horizontal = PauseTheme.spacing.md, vertical = PauseTheme.spacing.xs),
                    )
                    when (val c = command) {
                        is NudgeCommand.ShowCard -> NudgeCard(c.card, onAction = { controller.emit(it) })
                        is NudgeCommand.ShowPraise -> PraiseCard(c.text, onClose = { finish() })
                        else -> Unit
                    }
                }
            }
        }
    }
}

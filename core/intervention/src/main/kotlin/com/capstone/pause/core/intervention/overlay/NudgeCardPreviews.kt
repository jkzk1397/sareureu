package com.capstone.pause.core.intervention.overlay

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.intervention.nudge.CardContent
import com.capstone.pause.core.intervention.nudge.NudgeCommand
import com.capstone.pause.core.intervention.nudge.ReasonChip
import com.capstone.pause.core.model.BuddyExpression
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.MeltStage

private fun sample(level: Int) = CardContent(
    level = level,
    context = ContextSlot.PRE_SLEEP,
    appLabel = "유튜브",
    elapsedMinutes = 10 + level * 5,
    text = "자기 전 15분은 보통 40분이 되더라. 내일 아침의 너를 위해 여기서 끊을까?",
    copyId = "preview_l$level",
    reasonChips = if (level == 2) {
        listOf(ReasonChip("bored", "심심해서"), ReasonChip("tired", "피곤해서"), ReasonChip("avoid", "할 일 미루는 중"))
    } else {
        emptyList()
    },
    breathSeconds = if (level == 3) 10 else 0,
    typeToExtend = if (level == 3) "5분만 더 볼게" else null,
    question = if (level == 4) "지금 멈추면 내일 아침에 뭐가 달라질까?" else null,
    extendMinutes = if (level == 4) 3 else 5,
)

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    PauseTheme {
        Box(Modifier.background(PauseTheme.colors.scrim).padding(OverlayDimens.cardOuterPadding)) { content() }
    }
}

@Preview(name = "L1 light")
@Preview(name = "L1 dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun LevelOnePreview() = PreviewFrame { NudgeCard(sample(1), onAction = {}) }

@Preview(name = "L2 light", heightDp = 900)
@Preview(name = "L2 dark", heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun LevelTwoPreview() = PreviewFrame { NudgeCard(sample(2), onAction = {}) }

@Preview(name = "L3 light", heightDp = 1000)
@Preview(name = "L3 dark", heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun LevelThreePreview() = PreviewFrame { NudgeCard(sample(3), onAction = {}) }

@Preview(name = "L4 light", heightDp = 900)
@Preview(name = "L4 dark", heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun LevelFourPreview() = PreviewFrame { NudgeCard(sample(4), onAction = {}) }

@Preview(name = "L1 plan light")
@Preview(name = "L1 plan dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun LevelOnePlanPreview() = PreviewFrame { NudgeCard(sample(1).copy(planText = "폰은 책상 충전기에"), onAction = {}) }

@Preview(name = "L2 plan light", heightDp = 1000)
@Preview(name = "L2 plan dark", heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun LevelTwoPlanPreview() = PreviewFrame { NudgeCard(sample(2).copy(planText = "폰은 책상 충전기에"), onAction = {}) }

@Preview(name = "praise light")
@Preview(name = "praise dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PraisePreview() = PreviewFrame { PraiseCard(text = "멋지다! 15분에서 멈췄어", onClose = {}) }

@Preview(name = "bubble light")
@Preview(name = "bubble dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BubblePreview() = PreviewFrame {
    FloatingBuddy(sampleBubble, onTap = {})
}

@Preview(name = "bubble panel light")
@Preview(name = "bubble panel dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun BubblePanelPreview() = PreviewFrame {
    BubblePanel(sampleBubble, onStop = {}, onClose = {})
}

private val sampleBubble = NudgeCommand.ShowBubble(
    elapsedSec = 1_140,
    windowElapsedSec = 240,
    windowSec = 300,
    melt = MeltStage.SOFT,
    expression = BuddyExpression.CALLING,
    appLabel = "유튜브",
)

/**
 * 플로팅 버블이 한 구간 동안 0 → 100%로 녹는 모습을 흰 화면과 검은 화면 위에 나란히 놓은 판(ADR-0016).
 * 다른 앱 위에 뜨는 것이라 우리 테마 바탕이 아니라 순수한 흰색·검은색 위에서 테두리가 보이는지 본다.
 */
@Preview(name = "bubble melt on white and black", widthDp = 360)
@Composable
internal fun BubbleMeltStripPreview() {
    PauseTheme {
        Column {
            for (background in listOf(Color.White, Color.Black)) {
                // 프레임 6장을 세 개씩 두 줄로(실제 크기 64dp).
                for (steps in listOf(0..2, 3..MELT_STEPS)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(background)
                            .padding(vertical = PauseTheme.spacing.md),
                    ) {
                        for (step in steps) {
                            FloatingBuddy(
                                state = sampleBubble.copy(
                                    windowElapsedSec = sampleBubble.windowSec * step / MELT_STEPS,
                                    expression = if (step ==
                                        MELT_STEPS
                                    ) {
                                        BuddyExpression.CALLING
                                    } else {
                                        BuddyExpression.WATCHING
                                    },
                                ),
                                onTap = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MELT_STEPS = 5

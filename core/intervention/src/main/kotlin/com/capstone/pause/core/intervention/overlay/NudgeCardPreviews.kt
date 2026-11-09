package com.capstone.pause.core.intervention.overlay

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
private fun LevelOnePreview() = PreviewFrame { NudgeCard(sample(1), onAction = {}) }

@Preview(name = "L2 light", heightDp = 900)
@Preview(name = "L2 dark", heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LevelTwoPreview() = PreviewFrame { NudgeCard(sample(2), onAction = {}) }

@Preview(name = "L3 light", heightDp = 1000)
@Preview(name = "L3 dark", heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LevelThreePreview() = PreviewFrame { NudgeCard(sample(3), onAction = {}) }

@Preview(name = "L4 light", heightDp = 900)
@Preview(name = "L4 dark", heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LevelFourPreview() = PreviewFrame { NudgeCard(sample(4), onAction = {}) }

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
private fun BubblePanelPreview() = PreviewFrame {
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

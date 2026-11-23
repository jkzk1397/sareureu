package com.capstone.pause.core.intervention.overlay

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.designsystem.R as DesignR
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.component.NudgeCardSurface
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.intervention.R
import com.capstone.pause.core.intervention.nudge.NudgeCommand

/**
 * 플로팅 르르: 표정 56dp + 3dp 라임 링(64dp). 링은 다음 카드까지 이번 구간 진행이라 '5분만 더' 뒤에는 0부터 다시 찬다.
 * 곧 말 걸 때는 부르는 얼굴, '오늘은 그만'이면 조는 얼굴. 탭하면 [onTap](미니 패널).
 */
@Composable
fun FloatingBuddy(state: NudgeCommand.ShowBubble, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.intervention_bubble_description,
        stringResource(DesignR.string.buddy_name),
        (state.elapsedSec / SECONDS_PER_MINUTE).toInt(),
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(OverlayDimens.bubbleWithRing)
            .background(PauseTheme.colors.surface, PauseTheme.shapes.pill)
            .clickable(role = Role.Button, onClick = onTap)
            .semantics { contentDescription = description },
    ) {
        CircularProgressIndicator(
            progress = { state.progress },
            color = PauseTheme.colors.accent,
            trackColor = PauseTheme.colors.surfaceAlt,
            strokeWidth = OverlayDimens.ringStroke,
            modifier = Modifier.size(OverlayDimens.bubbleWithRing),
        )
        BuddyAvatar(expression = state.expression, size = OverlayDimens.bubbleBuddy, melt = state.melt)
    }
}

/**
 * 버블을 끄는 동안 화면 아래 가운데에 뜨는 숨기기 과녁(X). 버블이 위에 오면([active]) 커지고 색이 바뀐다.
 * 여기에 놓으면 버블이 숨고 상단바 타이머로 바뀐다.
 */
@Composable
fun DismissTarget(active: Boolean, modifier: Modifier = Modifier) {
    val colors = PauseTheme.colors
    val size by animateDpAsState(
        targetValue = if (active) OverlayDimens.dismissTargetActive else OverlayDimens.dismissTarget,
        label = "dismissTarget",
    )
    val description = stringResource(R.string.intervention_dismiss_target)
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(OverlayDimens.dismissTargetActive)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .background(if (active) colors.nudge else colors.ink, PauseTheme.shapes.pill)
                .semantics { contentDescription = description },
        ) {
            Icon(
                painter = painterResource(R.drawable.intervention_ic_close),
                contentDescription = null,
                tint = if (active) colors.surface else colors.bg,
            )
        }
    }
}

/** 버블을 누르면 뜨는 작은 패널: 지금 몇 분째인지, 다음 카드까지 얼마인지, 바로 멈추기, 숨기기. */
@Composable
fun BubblePanel(
    state: NudgeCommand.ShowBubble,
    onStop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onHide: (() -> Unit)? = null,
) {
    val minutes = (state.elapsedSec / SECONDS_PER_MINUTE).toInt()
    val app = state.appLabel.ifBlank { stringResource(R.string.intervention_app_other) }
    NudgeCardSurface(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
        ) {
            BuddyAvatar(expression = state.expression, size = OverlayDimens.cardBuddySmall, melt = state.melt)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.intervention_card_title, app, minutes),
                    style = PauseTheme.type.title,
                    color = PauseTheme.colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = when {
                        state.snoozed -> stringResource(R.string.intervention_panel_snoozed)
                        state.minutesToNext <= 0 -> stringResource(R.string.intervention_panel_soon)
                        else -> stringResource(R.string.intervention_panel_next, state.minutesToNext)
                    },
                    style = PauseTheme.type.bodySmall,
                    color = PauseTheme.colors.ink2,
                )
            }
        }
        PrimaryButton(text = stringResource(R.string.intervention_stop), onClick = onStop)
        // 끌어서 숨기기를 못 하는 경우(TalkBack 등)를 위한 같은 동작.
        onHide?.let { hide ->
            TextButton(
                onClick = hide,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PauseTheme.spacing.minTouch),
            ) {
                Text(
                    text = stringResource(R.string.intervention_panel_hide),
                    style = PauseTheme.type.label,
                    color = PauseTheme.colors.ink,
                )
            }
        }
        TextButton(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(
                text = stringResource(R.string.intervention_panel_keep),
                style = PauseTheme.type.label,
                color = PauseTheme.colors.ink2,
            )
        }
    }
}

private const val SECONDS_PER_MINUTE = 60L

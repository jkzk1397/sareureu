package com.capstone.pause.core.intervention.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.designsystem.R as DesignR
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.intervention.R
import com.capstone.pause.core.intervention.nudge.NudgeCommand
import com.capstone.pause.core.model.BuddyExpression

/** 플로팅 르르: 보는 중 표정 56dp + 3dp 라임 경과 링(64dp). 탭하면 [onTap]. */
@Composable
fun FloatingBuddy(state: NudgeCommand.ShowBubble, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val ratio = state.elapsedSec.toFloat() / state.thresholdSec.coerceAtLeast(1)
    val progress = ratio.coerceIn(0f, 1f)
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
            progress = { progress },
            color = PauseTheme.colors.accent,
            trackColor = PauseTheme.colors.surfaceAlt,
            strokeWidth = OverlayDimens.ringStroke,
            modifier = Modifier.size(OverlayDimens.bubbleWithRing),
        )
        BuddyAvatar(expression = BuddyExpression.WATCHING, size = OverlayDimens.bubbleBuddy, melt = state.melt)
    }
}

private const val SECONDS_PER_MINUTE = 60L

package com.capstone.pause.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import java.time.LocalTime

/** 온보딩 한 화면의 틀: 위 진행 막대(+뒤로), 스크롤 본문, 아래 버튼. */
@Composable
internal fun OnboardingFrame(
    step: OnboardingStep,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    primaryText: String? = null,
    onPrimary: () -> Unit = {},
    primaryEnabled: Boolean = true,
    secondaryText: String? = null,
    onSecondary: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        ProgressBar(step = step, onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screen, vertical = spacing.xs),
            content = content,
        )
        if (primaryText != null || secondaryText != null) {
            Column(Modifier.padding(horizontal = spacing.screen, vertical = spacing.sm)) {
                primaryText?.let { PrimaryButton(text = it, onClick = onPrimary, enabled = primaryEnabled) }
                secondaryText?.let {
                    TextButton(
                        onClick = onSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = spacing.minTouch),
                    ) {
                        Text(text = it, style = PauseTheme.type.label, color = colors.ink2)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(step: OnboardingStep, onBack: (() -> Unit)?) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    val total = OnboardingStep.SECTIONS
    val a11y = stringResource(R.string.onboarding_progress_a11y, step.section, total)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xs, vertical = spacing.xs),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(spacing.minTouch)) {
                Icon(
                    painter = painterResource(R.drawable.onboarding_ic_back),
                    contentDescription = stringResource(R.string.onboarding_back),
                    tint = colors.ink,
                )
            }
        } else {
            Box(Modifier.size(spacing.minTouch))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = a11y },
        ) {
            repeat(total) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(PauseSizes.barGap * 3)
                        .background(if (i < step.section) colors.ink else colors.line, PauseTheme.shapes.pill),
                )
            }
        }
        Text(
            text = stringResource(R.string.onboarding_progress, step.section, total),
            style = PauseTheme.type.label,
            color = colors.ink2,
            modifier = Modifier.padding(end = spacing.sm),
        )
    }
}

/** 단계 제목 + 설명. */
@Composable
internal fun StepHeader(title: String, body: String?) {
    Text(
        text = title,
        style = PauseTheme.type.headline,
        color = PauseTheme.colors.ink,
        modifier = Modifier.semantics { heading() },
    )
    body?.let { Text(text = it, style = PauseTheme.type.body, color = PauseTheme.colors.ink2) }
}

/** 칩 여러 개를 줄바꿈해서 놓는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
    ) { content() }
}

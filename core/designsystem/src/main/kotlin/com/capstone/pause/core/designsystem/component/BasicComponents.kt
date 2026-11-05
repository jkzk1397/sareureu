package com.capstone.pause.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme

/** 로고 워드마크. 색은 `ink`, 기본 높이 22dp(최소 18dp). */
@Composable
fun Wordmark(modifier: Modifier = Modifier, height: Dp = PauseSizes.wordmarkHeight) {
    Image(
        painter = painterResource(R.drawable.designsystem_wordmark),
        contentDescription = stringResource(R.string.app_name),
        colorFilter = ColorFilter.tint(PauseTheme.colors.ink),
        contentScale = ContentScale.FillHeight,
        modifier = modifier.height(maxOf(height, PauseSizes.wordmarkMinHeight)),
    )
}

/** 28dp 카드: `surface` 바탕 + `line` 1dp 테두리, 제목·오른쪽 끝·내용 슬롯. */
@Composable
fun PauseCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    containerColor: Color = PauseTheme.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = PauseTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor, PauseTheme.shapes.card)
            .border(spacing.hairline, PauseTheme.colors.line, PauseTheme.shapes.card)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (title != null || trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Text(
                        text = title,
                        style = PauseTheme.type.title,
                        color = PauseTheme.colors.ink2,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                }
                trailing?.invoke()
            }
        }
        content()
    }
}

/** 기준 대비 변화 방향. 늘어난 숫자를 빨갛게 칠하지 않는다. */
enum class ChangeDirection { DOWN, UP, NONE }

/** 변화 글자: 줄면 `success` + ▼, 늘면 `ink2` + ▲. */
@Composable
fun ChangeText(text: String, direction: ChangeDirection, modifier: Modifier = Modifier) {
    val colors = PauseTheme.colors
    val (label, color) = when (direction) {
        ChangeDirection.DOWN -> stringResource(R.string.designsystem_change_down, text) to colors.success
        ChangeDirection.UP -> stringResource(R.string.designsystem_change_up, text) to colors.ink2
        ChangeDirection.NONE -> text to colors.ink2
    }
    Text(text = label, style = PauseTheme.type.label, color = color, modifier = modifier)
}

/** 큰 숫자 + 기준선 대비 변화. */
@Composable
fun StatHero(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    change: String? = null,
    direction: ChangeDirection = ChangeDirection.NONE,
    valueStyle: TextStyle = PauseTheme.type.display,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xxs)) {
        Text(text = caption, style = PauseTheme.type.bodySmall, color = PauseTheme.colors.ink2)
        Text(text = value, style = valueStyle, color = PauseTheme.colors.ink)
        if (change != null) ChangeText(change, direction)
    }
}

/** 주 버튼: 라이트는 잉크 바탕 + 라임 글자, 다크는 반전. 최소 48dp, 알약 모양. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = PauseTheme.shapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = PauseTheme.colors.primary,
            contentColor = PauseTheme.colors.onPrimary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch),
    ) {
        Text(text = text, style = PauseTheme.type.title)
    }
}

/** "시연 데이터" 배지. 시연 데이터가 보일 때 늘 함께 띄운다. */
@Composable
fun DemoBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.designsystem_demo_badge),
        style = PauseTheme.type.caption,
        color = PauseTheme.colors.onAccentContainer,
        modifier = modifier
            .background(PauseTheme.colors.accentContainer, PauseTheme.shapes.pill)
            .padding(horizontal = PauseTheme.spacing.xs, vertical = PauseTheme.spacing.xxs),
    )
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BasicComponentsPreview() {
    PauseTheme {
        Column(
            Modifier
                .background(PauseTheme.colors.bg)
                .padding(PauseTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
        ) {
            Wordmark()
            DemoBadge()
            PauseCard(title = "오늘 하루 시계", trailing = { ChangeText("23분 어제보다", ChangeDirection.DOWN) }) {
                StatHero(value = "1시간 12분", caption = "오늘 숏폼", change = "8분", direction = ChangeDirection.UP)
            }
            PrimaryButton(text = "지금 그만할래", onClick = {})
        }
    }
}

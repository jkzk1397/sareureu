package com.capstone.pause.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.theme.PauseColors
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.BuddyExpression

/**
 * 랭킹 한 줄: 순위·아바타(첫 글자)·이름·감소율·기록률 배지. "나"는 `accentContainer`.
 * [coveragePercent]가 null이면 '측정 중'. 줄었으면([reductionDirection] DOWN) `success`, 늘었으면 `ink2`(빨갛게 칠하지 않는다, 06 문서 2.3절).
 */
@Composable
fun RankRow(
    rank: Int?,
    name: String,
    reductionLabel: String,
    coveragePercent: Int?,
    isMe: Boolean,
    modifier: Modifier = Modifier,
    reductionDirection: ChangeDirection = ChangeDirection.DOWN,
) {
    val colors = PauseTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch)
            .background(if (isMe) colors.accentContainer else colors.surface, PauseTheme.shapes.small)
            .padding(horizontal = PauseTheme.spacing.sm, vertical = PauseTheme.spacing.xs),
    ) {
        Text(text = rank?.toString() ?: "–", style = PauseTheme.type.title, color = colors.ink)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PauseSizes.rankAvatar)
                .background(if (isMe) colors.accent else colors.social, CircleShape),
        ) {
            Text(
                text = name.take(1),
                style = PauseTheme.type.label,
                color = if (isMe) PauseColors.BuddyInk else colors.surface,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = if (isMe) stringResource(R.string.designsystem_rank_me) else name,
                style = PauseTheme.type.body,
                color = if (isMe) colors.onAccentContainer else colors.ink,
            )
            Text(
                text = coveragePercent?.let { stringResource(R.string.designsystem_rank_coverage, it) }
                    ?: stringResource(R.string.designsystem_rank_measuring),
                style = PauseTheme.type.caption,
                color = colors.ink2,
            )
        }
        Text(
            text = reductionLabel,
            style = PauseTheme.type.title,
            color = if (reductionDirection == ChangeDirection.DOWN) colors.success else colors.ink2,
        )
    }
}

/**
 * 권한 고지 화면 틀(무엇을 / 왜 / 하지 않는 것 / 거절하면). 설정 화면으로 보내기 직전에 띄운다.
 * 뒤로 가기는 동의가 아니다.
 */
@Composable
fun PermissionExplainer(
    title: String,
    what: String,
    why: String,
    notDone: String,
    ifDenied: String,
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PauseTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bg)
            .padding(PauseTheme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
    ) {
        BuddyAvatar(BuddyExpression.NEUTRAL, PauseSizes.bubbleBuddy)
        Text(
            text = title,
            style = PauseTheme.type.headline,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        ExplainerItem(stringResource(R.string.designsystem_permission_what), what)
        ExplainerItem(stringResource(R.string.designsystem_permission_why), why)
        ExplainerItem(stringResource(R.string.designsystem_permission_not), notDone)
        ExplainerItem(stringResource(R.string.designsystem_permission_if_denied), ifDenied)
        PrimaryButton(text = primaryText, onClick = onPrimary)
        TextButton(
            onClick = onSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(text = secondaryText, style = PauseTheme.type.label, color = colors.ink2)
        }
    }
}

@Composable
private fun ExplainerItem(label: String, body: String) {
    PauseCard(title = label) {
        Text(text = body, style = PauseTheme.type.body, color = PauseTheme.colors.ink)
    }
}

/** 오버레이 카드 틀. 오버레이 카드만 그림자(8dp)를 쓴다. */
@Composable
fun NudgeCardSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = PauseTheme.shapes.card,
        color = PauseTheme.colors.surface,
        shadowElevation = PauseTheme.spacing.overlayElevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(PauseTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
            content = content,
        )
    }
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SurfaceComponentsPreview() {
    PauseTheme {
        Column(
            Modifier
                .background(PauseTheme.colors.bg)
                .padding(PauseTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm),
        ) {
            RankRow(1, "하은", "▼ 31%", 92, isMe = false)
            RankRow(2, "나", "▼ 23%", 91, isMe = true)
            RankRow(null, "준호", "–", null, isMe = false)
            NudgeCardSurface {
                Text("15분 넘었어. 잠깐 쉬어 갈래?", style = PauseTheme.type.title, color = PauseTheme.colors.ink)
                PrimaryButton(text = "그만 볼래", onClick = {})
            }
        }
    }
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PermissionExplainerPreview() {
    PauseTheme {
        PermissionExplainer(
            title = "사용 기록 접근",
            what = "어떤 앱을 언제 열었는지",
            why = "숏폼을 본 시간을 세려고",
            notDone = "화면 글자·영상 제목은 저장하지 않아",
            ifDenied = "시연 모드로 둘러볼 수 있어",
            primaryText = "동의하고 설정으로 이동",
            onPrimary = {},
            secondaryText = "나중에",
            onSecondary = {},
        )
    }
}

package com.capstone.pause.core.designsystem.component

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.ContextSlot

/** 맥락 아이콘. 색만으로 맥락을 알리지 않도록 늘 아이콘·이름과 함께 쓴다. */
@DrawableRes
fun ContextSlot.iconRes(): Int = when (this) {
    ContextSlot.WAKE_UP -> R.drawable.designsystem_ic_ctx_wake_up
    ContextSlot.COMMUTE -> R.drawable.designsystem_ic_ctx_commute
    ContextSlot.CLASS_WORK -> R.drawable.designsystem_ic_ctx_class_work
    ContextSlot.MEAL -> R.drawable.designsystem_ic_ctx_meal
    ContextSlot.BREAK -> R.drawable.designsystem_ic_ctx_break
    ContextSlot.PRE_SLEEP -> R.drawable.designsystem_ic_ctx_pre_sleep
    ContextSlot.LATE_NIGHT -> R.drawable.designsystem_ic_ctx_late_night
    ContextSlot.OTHER -> R.drawable.designsystem_ic_ctx_other
}

/** 맥락 이름 문자열. */
@StringRes
fun ContextSlot.labelRes(): Int = when (this) {
    ContextSlot.WAKE_UP -> R.string.designsystem_ctx_wake_up
    ContextSlot.COMMUTE -> R.string.designsystem_ctx_commute
    ContextSlot.CLASS_WORK -> R.string.designsystem_ctx_class_work
    ContextSlot.MEAL -> R.string.designsystem_ctx_meal
    ContextSlot.BREAK -> R.string.designsystem_ctx_break
    ContextSlot.PRE_SLEEP -> R.string.designsystem_ctx_pre_sleep
    ContextSlot.LATE_NIGHT -> R.string.designsystem_ctx_late_night
    ContextSlot.OTHER -> R.string.designsystem_ctx_other
}

/** 맥락 칩: 색 점 + 아이콘 + 이름. */
@Composable
fun ContextChip(slot: ContextSlot, modifier: Modifier = Modifier) {
    val colors = PauseTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xxs),
        modifier = modifier
            .heightIn(min = PauseSizes.chipMinHeight)
            .background(colors.surfaceAlt, PauseTheme.shapes.pill)
            .padding(horizontal = PauseTheme.spacing.sm),
    ) {
        ColorDot(slot)
        Icon(
            painter = painterResource(slot.iconRes()),
            contentDescription = null,
            tint = colors.ink2,
            modifier = Modifier.size(PauseSizes.iconSmall),
        )
        Text(text = stringResource(slot.labelRes()), style = PauseTheme.type.label, color = colors.ink2)
    }
}

/** 범례 한 줄: 색 점 + 아이콘 + 이름 + 값(목업 01). */
@Composable
fun ContextLegendRow(slot: ContextSlot, value: String, modifier: Modifier = Modifier) {
    val colors = PauseTheme.colors
    val name = stringResource(slot.labelRes())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = PauseSizes.chipMinHeight)
            .clearAndSetSemantics { contentDescription = "$name $value" },
    ) {
        ColorDot(slot)
        Spacer(Modifier.width(PauseTheme.spacing.xs))
        Icon(
            painter = painterResource(slot.iconRes()),
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(PauseSizes.iconSmall),
        )
        Spacer(Modifier.width(PauseTheme.spacing.xs))
        Text(text = name, style = PauseTheme.type.body, color = colors.ink, modifier = Modifier.weight(1f))
        Text(text = value, style = PauseTheme.type.body, color = colors.ink2)
    }
}

/** 맥락 색 점. */
@Composable
fun ColorDot(slot: ContextSlot, modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .size(PauseSizes.legendDot)
            .background(PauseTheme.colors.contextColor(slot), CircleShape),
    )
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContextUiPreview() {
    PauseTheme {
        Column(
            Modifier
                .background(PauseTheme.colors.surface)
                .padding(PauseTheme.spacing.md),
        ) {
            ContextSlot.entries.forEach { ContextLegendRow(it, "12분") }
            ContextChip(ContextSlot.PRE_SLEEP)
        }
    }
}

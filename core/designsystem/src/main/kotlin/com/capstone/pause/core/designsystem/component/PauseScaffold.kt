package com.capstone.pause.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.designsystem.theme.PauseTheme

/** 탭 화면의 기본 틀: `bg` 바탕, 좌우 20dp 여백, 세로 스크롤, 제목. */
@Composable
fun PauseScreenColumn(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val spacing = PauseTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PauseTheme.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = PauseTheme.type.headline,
                color = PauseTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
    }
}

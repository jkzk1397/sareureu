package com.capstone.pause.feature.coach

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.capstone.pause.core.ai.coach.EvidenceView
import com.capstone.pause.core.ai.coach.ReplyDetails
import com.capstone.pause.core.ai.engine.safety.SafetyFilter
import com.capstone.pause.core.ai.engine.schema.PlanProposalOut
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.ContextSlot
import java.time.ZoneId
import java.time.ZonedDateTime

private val zone = ZoneId.of("Asia/Seoul")
private val today = ZonedDateTime.of(2026, 11, 3, 21, 0, 0, 0, zone)

/** 목업 07 수치(가상). */
private fun previewState(crisis: Boolean = false) = CoachUiState(
    items = listOf(
        ChatItem(
            1,
            today.minusDays(1).withHour(23).withMinute(42),
            ChatItem.From.Buddy,
            "너 아직도 유튜브 보고 있어? 눈 감고 노래 한 곡 어때",
            true,
            null,
        ),
        ChatItem(2, today, ChatItem.From.Me, "나 왜 자기 전에 못 끊을까", false, null),
        ChatItem(
            3,
            today,
            ChatItem.From.Buddy,
            "이번 주 자기 전에 3시간 28분 봤고, 그중 7번은 메신저에서 넘어왔어. 누워서 메신저 정리하다가 이어진 것 같은데, 맞아?",
            false,
            ReplyDetails(
                messageId = 3,
                aiCallId = 1,
                evidence = listOf(
                    EvidenceView.Context(ContextSlot.PRE_SLEEP, 208),
                    EvidenceView.Flow("MESSENGER", "YOUTUBE", "SCREEN_OFF", 7),
                ),
                followUps = listOf("맞아", "반쯤", "출근길엔 뭐 해?"),
                planProposal = PlanProposalOut("누우면", "메신저는 책상에서 정리하고 폰은 거기 두기"),
                usedRealAi = false,
            ),
        ),
    ),
    crisis = if (crisis) listOf(SafetyFilter.SUICIDE_PREVENTION, SafetyFilter.MENTAL_HEALTH_CRISIS) else null,
    today = today,
)

@Preview(name = "light", heightDp = 1200)
@Preview(name = "dark", heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CoachScreenPreview() {
    PauseTheme { CoachScreen(uiState = previewState(), onAction = {}) }
}

@Preview(name = "light crisis", heightDp = 1400)
@Preview(name = "dark crisis", heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CoachCrisisPreview() {
    PauseTheme { CoachScreen(uiState = previewState(crisis = true), onAction = {}) }
}

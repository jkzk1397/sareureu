package com.capstone.pause.core.intervention.nudge

import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.MeltStage

/**
 * 개입 엔진(규칙)과 화면(오버레이·미리보기 액티비티) 사이의 계약.
 * 엔진은 [NudgeCommand]만 내보내고, 화면은 [NudgeUserAction]만 돌려준다. AI는 여기에 끼지 않는다.
 */
sealed interface NudgeCommand {
    /** 플로팅 르르(경과 링). */
    data class ShowBubble(val elapsedSec: Long, val thresholdSec: Long, val melt: MeltStage) : NudgeCommand

    /** 카드 1~4단계. */
    data class ShowCard(val card: CardContent) : NudgeCommand

    /** 메시지 알림(헤드업). 르르 또는 친구가 맡긴 잔소리. 서비스가 `PauseNotifier`로 보낸다([NudgeUi]는 무시해도 된다). */
    data class PostBuddyMessage(val message: OutgoingMessage) : NudgeCommand

    /** '그만' 뒤 칭찬 한마디(잠깐 보이고 사라짐). */
    data class ShowPraise(val text: String) : NudgeCommand

    /** 버블·카드 모두 내리기. */
    data object Hide : NudgeCommand
}

/** 카드 한 장의 내용. 문구는 문구 팩에서 골라 자리표시자를 채운 완성본이다. */
data class CardContent(
    /** 1~4. */
    val level: Int,
    val context: ContextSlot,
    /** 표시용 앱 이름(문자열 리소스로 이미 바꾼 값). */
    val appLabel: String,
    val elapsedMinutes: Int,
    /** 맥락 문구(완성본). */
    val text: String,
    /** 문구 팩 ID(감사·통계용). */
    val copyId: String?,
    /** 2단계: 이유 칩(키와 표시 문구). */
    val reasonChips: List<ReasonChip> = emptyList(),
    /** 3단계: 숨 고르기 초. */
    val breathSeconds: Int = 0,
    /** 3단계: 연장하려면 그대로 입력해야 하는 문장. */
    val typeToExtend: String? = null,
    /** 4단계: 답해야 연장되는 질문. */
    val question: String? = null,
    /** 연장 길이(분): 1~3단계 5, 4단계 3. */
    val extendMinutes: Int = 5,
    /** 2단계부터 '오늘은 그만 알려 줘'. */
    val canSnoozeToday: Boolean = level >= 2,
)

/** 2단계 이유 칩: 심심해서·피곤해서·할 일 미루는 중·재밌어서·그냥. */
data class ReasonChip(val key: String, val label: String)

/** 메시지 알림 내용. [senderName]은 "르르 · 사르르" / "민지 · 맡겨 둔 잔소리"처럼 완성된 이름. */
data class OutgoingMessage(
    val senderName: String,
    val fromFriend: Boolean,
    val text: String,
    val copyId: String?,
    val pokeId: String? = null,
)

/** 사용자가 화면에서 한 일. */
sealed interface NudgeUserAction {
    /** 그만 볼래(모든 단계 1탭 → 홈). */
    data class Stop(val level: Int) : NudgeUserAction

    /** 연장. 2단계는 [reasonChip], 3단계는 입력 완료, 4단계는 [answer]. */
    data class Extend(val level: Int, val reasonChip: String? = null, val answer: String? = null) : NudgeUserAction

    /** 오늘은 그만 알려 줘. */
    data class SnoozeToday(val level: Int) : NudgeUserAction

    /** 카드를 닫기만 함(아무 선택 없이). */
    data class Dismiss(val level: Int) : NudgeUserAction

    /** 버블 탭(미니 패널 열기). */
    data object BubbleTapped : NudgeUserAction
}

/**
 * 명령을 그리는 쪽. 오버레이 권한이 있으면 `OverlayController`, 없으면 `NudgePreviewActivity`를 띄우는 구현.
 * 서비스가 하나만 주입받아 쓴다.
 */
interface NudgeUi {
    /** 명령 그리기(메인 스레드에서 부른다). */
    fun render(command: NudgeCommand)

    /** 사용자 동작. */
    val actions: kotlinx.coroutines.flow.SharedFlow<NudgeUserAction>
}

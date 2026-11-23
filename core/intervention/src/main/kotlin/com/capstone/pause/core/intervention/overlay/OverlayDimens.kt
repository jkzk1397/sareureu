package com.capstone.pause.core.intervention.overlay

import androidx.compose.ui.unit.dp

/** 오버레이 전용 크기(06 문서 8절 '크기와 자리' 표 값). 색·간격·모양은 디자인 토큰을 쓴다. */
internal object OverlayDimens {
    /** 플로팅 버블 르르. */
    val bubbleBuddy = 56.dp

    /** 진행 링 포함 버블. */
    val bubbleWithRing = 64.dp

    /** 진행 링 두께. */
    val ringStroke = 3.dp

    /** 카드 1단계 위 르르. */
    val cardBuddyLarge = 72.dp

    /** 카드 2·3단계 말풍선 르르. */
    val cardBuddySmall = 56.dp

    /** 3단계 숨 고르기 링 지름. */
    val breathRing = 140.dp

    /** 3단계 숨 고르기 링 두께. */
    val breathStroke = 10.dp

    /** 오버레이 카드 바깥 여백. */
    val cardOuterPadding = 12.dp

    /** 버블 숨기기 과녁(아래 가운데 X). */
    val dismissTarget = 64.dp

    /** 과녁 위에 버블이 들어오면 커지는 크기. */
    val dismissTargetActive = 80.dp

    /** 과녁과 화면 아래 사이. */
    val dismissTargetBottom = 72.dp

    /** 버블 중심이 과녁 중심에서 이 거리 안이면 숨긴다. */
    val dismissRadius = 72.dp

    /** 버블이 화면 위·아래 끝에 붙지 않게 두는 여백. */
    val bubbleEdgeMargin = 48.dp
}

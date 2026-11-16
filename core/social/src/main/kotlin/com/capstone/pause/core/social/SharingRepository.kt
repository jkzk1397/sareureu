package com.capstone.pause.core.social

import kotlinx.coroutines.flow.Flow

/**
 * 친구 서버로 내 숫자를 보내는 공유 동의(server/README.md "앱 연결 계약").
 * 동의하면 하루 합계·감소율·기록된 비율을 하루 한 번 올리고, 철회하면 멈추고 서버 기록을 지운다.
 * 시연 기록은 절대 보내지 않는다.
 */
interface SharingRepository {
    /** 지금 공유 상태. */
    val state: Flow<SharingState>

    /** 동의하고 서버에 알린다. 서버가 받아 주면 true(그때만 동의를 기록한다). */
    suspend fun grant(): Boolean

    /** 철회: 업로드를 멈추고 서버의 내 기록을 지운다. 서버를 지우지 못했으면 false(로컬 철회는 한다). */
    suspend fun revoke(): Boolean

    /** 하루 합계와 내 감소율 올리기(WorkManager가 하루 한 번 부른다). 실제 기록만. 올렸으면 true. */
    suspend fun upload(): Boolean
}

/** 공유 상태. */
enum class SharingState {
    /** 서버 주소가 없거나 시연 모드(가상 친구). */
    UNAVAILABLE,

    /** 서버는 있지만 아직 동의하지 않음(친구 탭에 동의 화면). */
    NOT_CONSENTED,

    /** 동의함(친구 연결·랭킹·잔소리 사용). */
    CONSENTED,
}

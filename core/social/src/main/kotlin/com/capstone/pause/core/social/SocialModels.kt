package com.capstone.pause.core.social

/**
 * 랭킹에 쓰는 한 사람(나 또는 친구). 친구에게 공유되는 숫자는 감소율과 기록률뿐이다(ADR-0005).
 * [reductionRate]는 0.23 = 23% 줄었음, 늘었으면 음수.
 */
data class Standing(
    val id: String,
    val nickname: String,
    val reductionRate: Double?,
    val coverage: Double?,
    val validDays: Int,
    val isMe: Boolean,
) {
    /** 유효한 날 5일 미만이거나 기록률 80% 미만이면 '측정 중'. */
    val measuring: Boolean get() = Ranking.isMeasuring(this)
}

/** 화면에 보일 순위 한 줄. */
data class RankedRow(val rank: Int, val standing: Standing)

/** 랭킹 카드 값: 보일 줄(상위 3 + 나)과 측정 중인 사람. */
data class RankingView(val rows: List<RankedRow>, val measuring: List<Standing>)

/** 함께 챌린지 한 줄: 월~일 달성 여부(미래 날은 null). */
data class ChallengeRow(val nickname: String, val isMe: Boolean, val days: List<Boolean?>)

/** 친구 탭 전체 값. */
data class FriendsOverview(
    val isDemo: Boolean,
    /** 친구 기능을 쓸 수 있는지(지금은 시연 모드의 가상 친구만). */
    val available: Boolean,
    val me: Standing,
    val ranking: RankingView,
    /** 잔소리를 맡길 수 있는 친구(차단 제외). */
    val friends: List<Standing>,
    /** 차단한 친구까지 모두(친구 관리용). */
    val allFriends: List<Standing>,
    val blocked: Set<String>,
    val reported: Set<String>,
    val challenge: List<ChallengeRow>,
    /** 친구 서버 공유 상태. NOT_CONSENTED면 친구 탭이 동의 화면을 보인다. */
    val sharing: SharingState = SharingState.UNAVAILABLE,
)

/** 초대 코드(6자리, [expiresAt]까지 한 번만 쓸 수 있다). */
data class InviteCode(val code: String, val expiresAt: java.time.Instant)

/** 초대 코드로 연결한 결과. */
sealed interface ConnectResult {
    /** 연결됨. */
    data class Connected(val nickname: String) : ConnectResult

    /** 코드가 없거나 시간이 지났음. */
    data object NotFound : ConnectResult

    /** 내 코드. */
    data object OwnCode : ConnectResult

    /** 이미 친구. */
    data object AlreadyFriends : ConnectResult

    /** 차단 관계라 연결할 수 없음(어느 쪽이 차단했는지는 알리지 않는다). */
    data object Unavailable : ConnectResult

    /** 네트워크·서버 문제. */
    data object Failed : ConnectResult
}

/** 주간 리그 상태(P1). */
sealed interface LeagueState {
    /** 아직 열리지 않음. */
    data object ComingSoon : LeagueState
}

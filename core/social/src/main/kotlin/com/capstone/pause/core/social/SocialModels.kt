package com.capstone.pause.core.social

/**
 * 랭킹에 쓰는 한 사람(나 또는 친구). 친구에게 공유되는 것은 감소율·기록률·연속 일수·지금 시청 중뿐이다(ADR-0012).
 * [reductionRate]는 0.23 = 23% 줄었음, 늘었으면 음수. [streakDays]는 목표 이하로 본 연속 일수(`GoalStreak`),
 * [watching]은 최근 5초 안에 숏폼을 보고 있었는지(서버 판정, 나는 항상 false).
 */
data class Standing(
    val id: String,
    val nickname: String,
    val reductionRate: Double?,
    val coverage: Double?,
    val validDays: Int,
    val isMe: Boolean,
    val streakDays: Int = 0,
    val watching: Boolean = false,
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
    /** 친구 목록을 서버에서 마지막으로 받은 시각. 가상 친구거나 아직 못 받았으면 null. */
    val syncedAt: java.time.Instant? = null,
    /**
     * 서버에 닿지 못해 저장해 둔 목록을 보이는 중. 이때는 [syncedAt] 기준이라고 알리고
     * 시청 중 표시와 잔소리 버튼을 끈다(ADR-0014).
     */
    val offline: Boolean = false,
)

/** 연결 요청 목록(ADR-0014: 코드를 넣으면 요청만 생기고 코드 주인이 수락해야 친구가 된다). */
data class FriendRequests(val received: List<ReceivedRequest> = emptyList(), val sent: List<SentRequest> = emptyList())

/** 내가 받은 요청. 수락 전에는 요청한 사람의 닉네임만 안다. */
data class ReceivedRequest(
    val id: String,
    val fromNickname: String,
    val createdAt: java.time.Instant,
    val expiresAt: java.time.Instant,
)

/** 내가 보낸 요청. 상대가 누구인지는 수락될 때까지 서버가 주지 않는다(거절도 알리지 않는다). */
data class SentRequest(val id: String, val createdAt: java.time.Instant, val expiresAt: java.time.Instant)

/** 서버 호출이 왜 안 됐는지(화면이 종류별로 다른 말을 하려고). */
sealed interface SocialError {
    /** 인터넷에 닿지 못함. */
    data object Offline : SocialError

    /** 서버 점검·오류(5xx). */
    data object Maintenance : SocialError

    /** 너무 많이 시도함(429). [retryAfterSeconds] 뒤에 다시 할 수 있다(모르면 null). */
    data class TooMany(val retryAfterSeconds: Long?) : SocialError

    /** 로그인이 만료됨(토큰을 새로 받아 한 번 더 해도 401). */
    data object SessionExpired : SocialError

    /** 폰 시계가 5분 넘게 틀림. */
    data object ClockSkew : SocialError

    /** 앱이 오래됨(426). 새 앱을 받아야 한다. */
    data object UpgradeRequired : SocialError

    /** 그 밖. */
    data object Unknown : SocialError
}

/** 받은 연결 요청을 수락한 결과. */
sealed interface AcceptResult {
    /** 친구가 됨. */
    data class Accepted(val nickname: String) : AcceptResult

    /** 요청이 없음(취소됨·만료됨·차단 관계). */
    data object NotFound : AcceptResult

    /** 나 또는 상대의 친구가 50명. */
    data object TooManyFriends : AcceptResult

    /** 서버에 닿지 못함 등. */
    data class Error(val error: SocialError) : AcceptResult
}

/** 신고 사유 종류(서버 `reports.mjs`와 같은 이름). */
enum class ReportReason {
    /** 잔소리 내용. */
    POKE,

    /** 닉네임. */
    NICKNAME,

    /** 괴롭힘(반복 요청·잔소리). */
    HARASSMENT,

    /** 그 밖. */
    OTHER,
}

/**
 * 초대 코드(6자리, [expiresAt]까지 한 번만 쓸 수 있다). [link]는 QR에 담는 주소(친구 서버의 `/invite/{코드}`):
 * 앱이 있으면 앱이 열려 바로 연결되고, 없으면 설치 파일로 간다.
 */
data class InviteCode(val code: String, val expiresAt: java.time.Instant, val link: String? = null)

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

    /** 네트워크·서버 문제(종류를 모를 때). */
    data object Failed : ConnectResult

    /** 연결 요청을 보냄. 코드 주인이 수락하면 친구가 된다(ADR-0014). */
    data class Requested(val requestId: String) : ConnectResult

    /** 내가 보낸 대기 요청이 가득 참(20개). */
    data object TooManyRequests : ConnectResult

    /** 코드 모양이 틀림(서버에 보내지 않았다). */
    data class InvalidCode(val check: CodeCheck) : ConnectResult

    /** 서버에 닿지 못함·한도·시계 등 종류가 있는 실패. */
    data class Error(val error: SocialError) : ConnectResult
}

/** 주간 리그 상태(P1). */
sealed interface LeagueState {
    /** 아직 열리지 않음. */
    data object ComingSoon : LeagueState
}

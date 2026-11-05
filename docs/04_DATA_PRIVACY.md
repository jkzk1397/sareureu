# 04. 데이터 모델과 개인정보

> 담당: R2(모델·DB), R6(원격), R1(동의 흐름) · 이 문서의 타입은 `core:model`의 계약이다. 바꾸려면 계약 PR.

## 1. 원칙
1. **원시 기록은 기기 밖으로 나가지 않는다.** 클라우드에는 동의한 합계와 친구 기능에 필요한 최소 정보만.
2. **최소 수집.** 분석에 쓰지 않는 항목은 묻지도 저장하지도 않는다(생년월일·성별·학교명·실명·위치 좌표 X).
3. **화면 내용은 읽어도 남기지 않는다.** 정밀 모드의 접근성은 판단(참/거짓)과 횟수만 저장.
4. **언제든 내보내고 지운다.** 설정 > 개인정보 > 내보내기(JSON) / 모든 기록 삭제(로컬 + 원격).
5. **동의는 기능별로, 앱 안에서, 거절해도 앱이 돈다.**

## 2. 도메인 타입 (core:model)
> 아래가 전부다. 여기 없는 타입을 `core:model`에 새로 만들려면 계약 PR.
```kotlin
enum class ContextSlot { WAKE_UP, COMMUTE, CLASS_WORK, MEAL, BREAK, PRE_SLEEP, LATE_NIGHT, OTHER }
enum class Confidence { HIGH, MEDIUM, LOW }
enum class AppKind { SHORTFORM, MESSENGER, SOCIAL, VIDEO, STUDY_WORK, BROWSER, GAME, SHOPPING, LAUNCHER, OTHER }
enum class Surface { SHORTS, REELS, TIKTOK_FEED, APP_UNKNOWN }          // APP_UNKNOWN = 앱 단위 모드

data class TrackedApp(val packageName: String, val label: String, val kind: AppKind, val enabled: Boolean)

sealed interface EntryPoint {                                          // 어디서 넘어왔나
  data object Unlock : EntryPoint                                      // 잠금 해제 10초 이내
  data object Home : EntryPoint                                        // 홈 화면(런처)에서
  data class FromApp(val kind: AppKind) : EntryPoint                   // 직전 앱 종류
  data class FromShortform(val packageName: String) : EntryPoint       // 다른 숏폼에서(도미노)
  data object Unknown : EntryPoint
}
sealed interface ExitPoint {                                           // 어디로 나갔나
  data object ScreenOff : ExitPoint
  data object Home : ExitPoint
  data class ToApp(val kind: AppKind) : ExitPoint
  data object AfterNudgeStop : ExitPoint                               // '그만' 뒤 60초 이내
  data object Unknown : ExitPoint
}

data class AppSegment(val packageName: String, val surface: Surface, val start: Instant, val end: Instant)

data class ShortformSession(
  val id: Long, val start: Instant, val end: Instant?,                 // end=null 이면 진행 중
  val segments: List<AppSegment>,                                       // 숏폼 앱끼리 60초 이내 전환은 한 세션
  val context: ContextSlot, val contextConfidence: Confidence, val contextConfirmed: Boolean,
  val entry: EntryPoint, val exit: ExitPoint?,
  val interaction: InteractionStats?,                                   // 정밀 모드에서만
  val maxFrictionLevel: Int, val extensions: Int, val stoppedByNudge: Boolean,
  val checkIn: Mood?, val source: DataSource,                           // REAL / DEMO
)
data class InteractionStats(val swipes: Int, val medianSwipeIntervalSec: Double, val likes: Int, val otherClicks: Int)

enum class NudgeResponse { STOP, EXTEND, SNOOZE_TODAY, DISMISSED, IGNORED }
data class NudgeEvent(val sessionId: Long, val at: Instant, val level: Int, val copyId: String?, val response: NudgeResponse, val reasonChip: String?)

data class BuddyMessage(val id: Long, val at: Instant, val sender: Sender, val text: String, val copyId: String?, val sessionId: Long?)
sealed interface Sender { data object Buddy : Sender; data class Friend(val friendId: String, val nickname: String) : Sender; data object Me : Sender }

data class Profile(
  val nickname: String, val ageBand: AgeBand, val occupation: Occupation,
  val rhythm: DayRhythm, val goals: Set<Goal>, val interests: Set<String>, val tone: Tone,
  val dailyGoalMinutes: Int = 60,                                         // 하루 숏폼 목표. 기준선이 생기기 전(첫 7일) 녹는 단계의 기준
)
enum class AgeBand { AGE_18_24, AGE_25_29, AGE_30_39, AGE_40_PLUS }   // 만 18세 미만은 가입 불가(ADR-0009)
enum class Occupation { UNIVERSITY, GRAD_OR_JOBSEEKER, OFFICE_WORKER, SHIFT_OR_FREELANCE, OTHER }
data class DayRhythm(
  val weekdayWake: LocalTime, val weekdaySleep: LocalTime, val weekendWake: LocalTime, val weekendSleep: LocalTime,
  val classOrWork: List<WeeklyBlock>, val commute: List<WeeklyBlock>, val meals: List<DailyWindow>,
)
enum class Tone { PLAYFUL, WARM, PLAIN }                                  // 친구처럼 / 다정하게 / 담백하게

data class IfThenPlan(val id: Long, val context: ContextSlot?, val ifText: String, val thenText: String, val createdAt: Instant, val active: Boolean)
data class Hypothesis(val id: String, val text: String, val confidence: Confidence, val evidence: List<EvidenceRef>, val userVerdict: Verdict?)
sealed interface EvidenceRef { data class Session(val id: Long) : EvidenceRef; data class Stat(val key: String) : EvidenceRef }
enum class Verdict { YES, PARTLY, NO }                                    // 맞아 / 반쯤 / 아니야
enum class Mood { GOOD, MEH, BAD }
enum class DataSource { REAL, DEMO }
enum class Goal { SLEEP, FOCUS, TIME_BACK, MOOD, OTHER }                  // 잠, 집중, 시간 되찾기, 기분
data class WeeklyBlock(val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime)
data class DailyWindow(val start: LocalTime, val end: LocalTime)

// 르르(06 문서 8절)
enum class BuddyExpression { NEUTRAL, WATCHING, CALLING, WORRIED, CHEER, SLEEPY }
enum class MeltStage { NONE, SOFT, PUDDLE, MELTED }                       // 0~3, 오늘 숏폼 ÷ 기준

// 문구 팩·전략(05 문서 5.2, 6절). JSON 파일과 AI 출력이 같은 타입
@Serializable data class CopyPack(val version: String, val card: Map<ContextSlot, CardCopy>, val chipReply: Map<String, List<CopyLine>>,
  val buddyMessage: List<CopyLine>, val praise: List<CopyLine>, val predictive: Map<ContextSlot, CopyLine>)
@Serializable data class CardCopy(val L1: List<CopyLine>, val L2: List<CopyLine>, val L3: List<CopyLine>, val L4Question: List<CopyLine>)
@Serializable data class CopyLine(val id: String, val text: String, val tones: Set<Tone>)
@Serializable data class Strategy(val id: String, val context: ContextSlot?, val ifText: String, val thenText: String)
```
- 시간은 `Instant`로 저장하고, 화면·맥락 계산 때만 `ZoneId.systemDefault()`로 바꾼다. 날짜 경계는 **새벽 4시**(밤샘 세션이 이틀로 쪼개지지 않게).
- 문자열 표시는 `strings.xml`에서. enum 이름을 화면에 그대로 쓰지 않는다.

## 3. 저장 위치와 보관 기간
| 데이터 | 출처 | 목적 | 저장 | 보관 | AI로 보냄 | 친구에게 |
|---|---|---|---|---|---|---|
| 앱 전환 원시 이벤트 | UsageStatsManager | 세션 만들기 | Room `raw_events` | 7일 | ✕ | ✕ |
| 숏폼 세션 | 계산 | 통계·분석 | Room `sessions` | 90일(설정 변경 가능) | 요약만 | ✕ |
| 정밀 모드 판단·횟수 | 접근성 | 숏폼 화면 판별, 넘김 횟수 | `sessions.interaction` | 90일 | 요약만 | ✕ |
| 개입 기록 | 앱 | 단계·응답 | Room `nudge_events` | 90일 | 요약만 | ✕ |
| 메시지 알림·채팅 | 앱·AI | 대화 | Room `messages` | 90일 | 최근 대화만(채팅 중) | ✕ |
| 프로필 | 사용자 입력 | 맥락 분류·말투·또래 비교 | DataStore | 삭제 전까지 | 연령대·신분·관심사·목표 | 닉네임만 |
| 계획·가설·판정 | 사용자·AI | 다음 분석 반영 | Room | 90일 | 요약만 | ✕ |
| AI 감사 로그 | 앱 | 투명성·평가 | Room `ai_calls` | 30일 | — | ✕ |
| 일별 합계(숏폼 분, 감소율, 기록률, 멈춤 수) | 계산 | 랭킹·또래 비교 | Firestore(동의 시) | 1년 | ✕ | 감소율·기록률만 |
| 친구 관계·잔소리 | 사용자 | 친구 기능 | Firestore | 삭제 전까지 / 잔소리는 7일 | ✕ | 해당 친구만 |

## 4. 동의와 권한
| 단계 | 내용 | 거절하면 |
|---|---|---|
| 연령 확인 | 연령대 선택. **만 18세 미만 선택 시 이용 안내 후 종료**(ADR-0009, 저장하지 않음) | — |
| 서비스 약관·개인정보 처리 안내 | 무엇을 기기에 저장하는지, 무엇이 밖으로 나가는지 표로 | 시작 불가 |
| 사용 기록 접근(`PACKAGE_USAGE_STATS`) | 설정 화면으로 보내기 전 고지 화면 | 시연 모드 안내, 기능 제한 |
| 알림(`POST_NOTIFICATIONS`, 13+) | 메시지 알림·예약 알림 | 오버레이만 사용 |
| 다른 앱 위에 표시(`SYSTEM_ALERT_WINDOW`) | 플로팅 캐릭터·카드 | 알림으로 대체 |
| 정밀 모드(접근성, 선택) | **별도 화면**의 눈에 띄는 고지 + 직접 동의 버튼. 읽는 것 / 저장하는 것 / 하지 않는 것(다른 앱 조작·글자 저장 안 함) | 앱 단위 측정 |
| AI 사용 방식 | 클라우드 / 기기 안(지원 기기) / 사용 안 함. 클라우드 선택 시 보내는 항목 표 | 모의 AI(규칙 기반) |
| 친구·랭킹 | 일별 합계 공유 동의, 공유 항목 미리보기 | 혼자 쓰기 |
| 또래 비교 참여 | 연령대·신분 + 일별 합계 익명 집계 동의 | 공개 통계로만 비교 |

- 접근성 고지는 앱 설명이나 개인정보처리방침에만 두면 안 되고, **앱 안 일반 흐름에서 별도로** 보여 줘야 한다(Play 정책, `02_RESEARCH.md` 참고).
- 동의 기록(항목, 버전, 시각)을 DataStore에 남기고, 고지 문구가 바뀌면 다시 묻는다.

## 5. 클라우드로 보내는 AI 입력(예시)
```json
{
  "profile": { "ageBand": "AGE_18_24", "occupation": "UNIVERSITY", "goals": ["SLEEP"], "interests": ["기타 연습", "러닝"], "tone": "PLAYFUL" },
  "week": { "avgMinPerDay": 96, "baselineMinPerDay": 124, "reductionRate": 0.23, "coverage": 0.91 },
  "byContext": [ { "slot": "PRE_SLEEP", "min": 212, "sessions": 9, "medianSessionMin": 21 } ],
  "riskWindows": [ { "day": "TUE", "hour": 23, "min": 64 } ],
  "topFlows": [ { "entry": "MESSENGER", "app": "YOUTUBE", "exit": "SCREEN_OFF", "count": 7, "medianMin": 18 } ],
  "nudges": { "shown": 14, "stopRate": 0.43, "extensions": 11 },
  "observations": [ { "id": "o1", "text": "자기 전 세션 9개 중 7개가 메신저에서 시작" } ]
}
```
보내지 않는 것: 패키지 목록 전체, 세션별 정확한 시각 목록(채팅의 도구 호출 결과는 예외: 사용자가 물은 범위만), 친구 이름, 연락처, 위치.
※ **무료 등급(Spark, Unpaid Services)의 Gemini API는 입력·출력을 Google이 서비스 개선에 쓰고 사람 검토자가 볼 수 있으며, 약관이 개인·민감 정보를 보내지 말라고 한다**(`02_RESEARCH.md` 5절). 그래서 위 예시처럼 **집계 숫자와 범주만** 보낸다. 닉네임·자유 입력 메모·친구 이름은 보내지 않고, 채팅의 사용자 문장은 `SafetyFilter`와 개인정보 마스킹(전화번호·이메일·이름 패턴)을 거친다.
※ 프롬프트가 Google 서버(국외)로 가므로 **개인정보 보호법 제28조의8(국외 이전)** 에 따라 이전받는 자·국가·항목·목적을 처리방침과 AI 동의 화면에 적는다. 실제 사용자 대상 운영(베타 이후)은 유료 등급(Blaze, 입력을 개선에 쓰지 않음)으로 옮긴다.

## 6. Room 스키마 규칙
- DB 이름 `pause.db`, `exportSchema = true`, `schemas/`를 커밋한다.
- 테이블: `raw_events`, `sessions`, `segments`, `nudge_events`, `messages`, `plans`, `hypotheses`, `weekly_reports`, `copy_pack`(미리 만든 문구), `ai_calls`, `friends_cache`, `pokes_inbox`.
- 인덱스: `sessions(start)`, `segments(sessionId)`, `nudge_events(sessionId)`.
- 버전을 올릴 때마다 `MigrationTestHelper` 테스트. 파괴적 마이그레이션 금지(시연 데이터 포함).

## 7. Firestore 구조 (P1, 친구·랭킹)
```
users/{uid}                 { nickname, ageBand, occupation, createdAt }        // 본인만 쓰기
users/{uid}/daily/{yyyyMMdd}{ shortformMin, reductionRate, coverage, stops }     // 본인 쓰기, 친구 읽기(감소율·기록률만 노출하는 함수 경유)
friendships/{pairId}        { a, b, status: pending|active, createdAt }
pokes/{toUid}/inbox/{id}    { fromUid, fromNickname, text, createdAt, expiresAt, notifySender }
leagues/{weekId}/groups/{g} { members[], tierOf }                                // 서버(함수)만 쓰기
cohorts/{weekId}/{ageBand_occupation} { n, p50, p75 }                            // n < 10이면 문서를 만들지 않음
```
- 보안 규칙: 자기 문서만 쓰기, 친구의 `daily`는 `reductionRate`·`coverage`만 읽을 수 있도록 집계용 하위 문서(`publicDaily`)로 분리.
- 잔소리는 받는 사람이 앱을 열거나 주기 동기화할 때 내려받아 로컬 `pokes_inbox`에 둔다 → 숏폼 2단계에서 꺼내 쓴다(실시간 푸시 불필요).
- 무료(Spark) 요금제에서는 Cloud Functions를 쓸 수 없으므로: 친구 랭킹은 클라이언트가 친구들의 `publicDaily`를 읽어 계산, 주간 리그·또래 집계는 P1에서 Blaze 요금제 + 함수로.

## 8. 시연 데이터
- `core:sensing`의 `DemoScenario`: 14일, 3가지 패턴(밤형·출퇴근형·알림 도미노형) 중 선택, 고정 시드로 항상 같은 결과.
- 시연 데이터는 `source = DEMO`로 표시하고 "시연 데이터" 배지를 화면에 띄운다. 실제 데이터와 섞지 않는다(시연 모드 끄면 숨김).
- 가상 친구 3명과 가상 코호트(연령대별 분포)는 `assets/demo/`의 JSON.

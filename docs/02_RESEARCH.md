# 02. 조사 — 시장·경쟁·근거·정책·기술

> 확인일 2026-11-05 · 담당: 발표 담당 + R1. 모든 값은 웹 검색으로 공식·1차 자료를 확인했다. 원자료(출처 URL·날짜·신뢰도 전부)는 `docs/research/`에 있다.
> - `research/market.md` 시장·경쟁·연구·수익·이름
> - `research/policy.md` 안드로이드·Play 정책, AI 모델, 법
> - `research/versions.md` 라이브러리 버전
>
> 이 문서는 그중 **설계를 바꾸는 결론**만 모았다. 숫자를 발표에 쓸 때는 원자료의 신뢰도 표기를 같이 확인한다.

## 1. 시장 — 20대와 숏폼
| 결론 | 근거 | 출처 |
|---|---|---|
| 숏폼은 거의 모두가 본다 | 동영상 이용자의 91.7%가 숏폼 이용(쇼츠 45.6%, 릴스 21.1%, 틱톡 18.9%) | 과기정통부·NIA 2025 스마트폰 과의존 실태조사([머니투데이, 2026-03-27](https://www.mt.co.kr/tech/2026/03/27/2026032710122977237)) |
| 20대가 특히 위험하다 | 청년층(20~30대) 과의존 위험군 29.5%, 전체 22.7%. 20대만 다시 계산하면 34.0%(2024 원자료, 연구자 계산) | 같은 조사 / 이건우, 자유기업원([PDF](https://cfe.org/aFiles/2025/f_seminar_1758786813.pdf)) |
| **"어디서 넘어와"가 핵심이다** | 숏폼 시청의 69.9%가 알고리즘 추천, 40.6%가 우연. 검색해서 보는 경우는 15.8%. 비의도적 시청이 약 5배, 20대가 가장 높다 | 한국언론진흥재단 미디어이슈 10권 5호([기자협회보, 2024-10-31](https://www.journalist.or.kr/news/article.html?no=56994)) |
| '5분만'은 20분이 된다 | 한 번 보기 시작하면 평균 21분 | 컨슈머인사이트 2025 상반기([다음, 2025-08-17](https://v.daum.net/v/20250817073212400)) |
| 20대는 하루 약 1시간 | 20대 하루 숏폼 55분(전체 44분) | 메조미디어 2024([기자협회보](https://www.journalist.or.kr/m/m_article.html?no=56813)) |
| 정책은 청소년에 몰려 있다 | 스마트쉼센터·디지털 디톡스 프로그램은 대부분 청소년 대상. 20대는 서울 청년센터 협약(2025-07) 정도 | [디지털데일리, 2026-08-06](https://www.ddaily.co.kr/page/view/2026080615121990014), [벤처스퀘어, 2025-07-17](https://www.venturesquare.net/993909) |
| 플랫폼도 움직인다 | 유튜브 쇼츠 일일 한도(2025-10, 사용자가 닫을 수 있음), 보호자 쇼츠 제한(2026-01) | [exchange4media](https://www.exchange4media.com/digital-news/youtube-rolls-out-time-limit-option-for-shorts-to-curb-excessive-scrolling-148598.html), [뉴닉](https://newneek.co/@newneek/article/38183) |

빈 곳: 정부 조사 원문의 **20대만의 숏폼 이용률**은 보도에 없어 확인하지 못했다(원문 표 확인 필요).

## 2. 경쟁
- **막기**: Opal(연 $99.99 미국 가격, DAU 100만), Jomo(연 ₩43,000, iOS 전용), 지키자, 눕따(₩4,400 1회), Brick(기기 $59).
- **늦추기(마찰)**: one sec(월 ₩5,000 / 연 ₩15,000), ScreenZen(무료), ClearSpace(연 $44.99), un:short(국내 개발, 30초 쿨다운 + 질문 — 가장 비슷함).
- **숏폼 피드 차단(안드로이드)**: StopScroll(27.8만+ 설치), Shortstop, Block Scroll.
- **국내 습관·랭킹**: 열품타(월 ₩3,900~5,900, 랭킹), 마이루틴, 챌린저스(참가비 챌린지), 도파민 디톡스(아바타 + 랭킹), FriendLock(친구 셀카로 잠금 해제).
- **생성형 AI**: Jomo는 사진 인증, Monk·FocusGuard는 일반 코칭 수준.
- **빈자리**: 숏폼 감지 + 내 기록으로 '언제·어디서'를 AI가 분석 + 보는 순간 도착하는 친구 메시지 + 한국어 캐릭터를 한꺼번에 하는 앱은 웹 검색 범위에서 찾지 못했다(없다는 증명은 아님).
- 이름 충돌: 위 앱 이름들, 그리고 팀의 이전 데모 이름(unscroll)과 사르르는 소리·뜻이 겹치지 않는다.

자세한 표(가격·성과·출처): `research/market.md` 3절.

## 3. 개입이 효과가 있다는 근거 → 설계
| 연구 | 결과 | 사르르에 넣은 것 |
|---|---|---|
| Grüning et al., *PNAS* 2023 (one sec, 현장 실험 N=280·6주 + 온라인 실험 N=500) | 현장: 실제 앱 열기 57% 감소. 온라인 실험: '그만두기' 선택지가 가장 효과가 컸고, 생각을 유도하는 문구만으로는 효과 없음 | 모든 단계에 1탭 '그만'(홈 인텐트) + 짧은 지연. 말 걸기만 하고 끝내지 않는다 |
| Haliburton et al., *CHI* 2024 (1,039명, 13.4주) | 닫기 비율 33%, 약 20일 뒤 정체, 익숙해짐 | 단계 사다리(1→4), 매주 새 문구 팩, 친구 잔소리, 녹는 단계로 새로움 유지 |
| Ko et al., *CSCW* 2015 (KAIST, NUGU, N=62) | 그룹 조건에서만 하루 사용 약 24% 감소, 혼자는 유의하지 않음 | 친구 랭킹(감소율), 잔소리 맡기기, 함께 챌린지 |
| Allcott et al., *AER* 2022 | SNS 사용의 31%가 자기통제 문제. 스스로 정하는 한도 도구가 사용을 지속적으로 줄임 | 맥락별 기준 시간을 사용자가 정한다 |
| Pieh et al., *BMC Medicine* 2025 (18~29세 RCT) | 사용 시간 감소 → 우울 증상 27% 감소, 실험이 끝나면 사용량이 원래대로 | 일회성이 아닌 상시 동반, 주간 리포트 |
| Rahmillah et al., *JMIR* 2023 (리뷰) | 앱 개입 효과는 엇갈리고 연구 질이 약함 | 기준선 대비 감소율을 직접 재는 것 자체가 기여 |

DOI·링크: `research/market.md` 4절.

## 4. 안드로이드·Google Play 정책 (설계 제약)
| 주제 | 확인한 규칙 | 우리 결정 | 출처 |
|---|---|---|---|
| 포그라운드 서비스 유형 | `specialUse` = 다른 유형에 해당하지 않는 정당한 용도, 매니페스트 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` + Play Console 심사. `dataSync`는 데이터 전송용, 하루 6시간 제한(target 35+), 부팅 시작 금지 | **`specialUse` 하나만** | [FGS 유형](https://developer.android.com/develop/background-work/services/fgs/service-types), [시간 제한](https://developer.android.com/develop/background-work/services/fgs/timeout) |
| Play 선언 | Policy > App content > Foreground service permissions: 기능 설명, 중단 시 영향, 사용자가 켜는 동영상. 모든 유형이 심사 대상 | 매니페스트 문구·콘솔 설명·동영상을 같게 | [Play 도움말](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en) |
| 백그라운드 시작 | target 31+는 백그라운드에서 시작 불가. Android 15부터 오버레이 권한 예외는 **오버레이 창이 보이는 중**일 때만 | 화면의 '감지 켜기'에서만 시작 | [제한](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [Android 15](https://developer.android.com/about/versions/15/behavior-changes-15) |
| 부팅 후 | Android 15의 부팅 시작 금지 목록(dataSync·camera·mediaPlayback·phoneCall·mediaProjection·microphone)에 `specialUse`는 없음 | 감지를 켜 둔 경우만 다시 시작 | 같은 곳 |
| 사용자 중지 | 13+ 작업 관리자에서 앱 전체 중지(콜백 없음), 14+ 상태 알림 스와이프 가능 | 다음 실행 때 `REASON_USER_REQUESTED` 확인 후 안내 | [사용자 중지](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping) |
| 사용 기록 | 권한은 설정에서 사용자가 켬. 잠금 상태면 `queryEvents`가 `null`. `MOVE_TO_*`는 지원 중단 → `ACTIVITY_RESUMED/PAUSED`. API 35에 필터 조회 추가. Android 15~17 변경 없음 | 최근 N분을 다시 조회해 상태 재구성 | AOSP `UsageStatsManager`, [API 35 diff](https://developer.android.com/sdk/api_diff/35-incr/changes/android.app.usage.UsageStatsManager) |
| 사용 기록의 Play 정책 | 전용 선언서는 없지만, 다른 앱 사용 기록은 사용자 데이터 정책의 민감 정보 → 앱 안 고지 + 처리방침 + Data safety | 권한 화면 직전 별도 고지 | [사용자 데이터](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en) |
| 접근성 API | 비보조 앱은 `isAccessibilityTool` 금지, 앱 안 별도 고지 + 직접 동의, 선언서 + 동영상(거절 흐름 포함). **앱이 스스로 행동을 시작·계획·실행하게 하는 사용은 금지**(2025-10-30 공지), 정해진 규칙 자동화는 허용. 2026-10-29 공지는 고지 요건을 명확히 함 | 정밀 모드는 선택(P1), 감지만, 조작 없음, 7개 패키지·2개 이벤트만 | [접근성 정책](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en), [2025 공지](https://support.google.com/googleplay/android-developer/answer/16550159) |
| Android 17 고급 보호 모드 | 켜면 접근성 도구로 분류되지 않은 앱의 접근성 권한을 막음 | 감지해서 정밀 모드 끄기 | [Android 도움말](https://support.google.com/android/answer/16339980?hl=en), [개발 문서](https://developer.android.com/privacy-and-security/advanced-protection-mode) |
| 오버레이 터치 | Android 12+ 합친 불투명도 0.8 초과 창을 통과한 터치는 차단. 다른 앱이 오버레이를 숨길 수 있음 | 작은 터치 가능한 창, 숨겨져도 안전하게 | [Android 12](https://developer.android.com/about/versions/12/behavior-changes-all) |
| 홈 이동 | 오버레이 권한은 백그라운드 액티비티 시작 예외. PendingIntent 위임 규칙은 14·15·17에서 바뀜 | 직접 `startActivity` | [백그라운드 시작](https://developer.android.com/guide/components/activities/background-starts) |
| targetSdk 36 | 엣지 투 엣지 끄기 불가, 예측형 뒤로 가기 기본(`onBackPressed` 안 불림), 큰 화면 방향 고정 무시 | `BackHandler`, insets 처리 | [Android 16](https://developer.android.com/about/versions/16/behavior-changes-16) |
| 알림 '대화' | 대화 섹션은 사람 사이의 실시간 소통용, long-lived shortcut 필요 | 단축키 없이 `MessagingStyle`만, 보낸 사람에 앱·캐릭터 이름 | [대화 알림](https://developer.android.com/develop/ui/views/notifications/conversations) |
| AI 생성 콘텐츠 | 앱 안에서 AI 출력을 신고할 수 있어야 함 | AI 문장마다 '신고' | [Play AI 콘텐츠](https://support.google.com/googleplay/android-developer/answer/13985936?hl=en) |
| 목표 API | 2026-08-31부터 신규·업데이트 targetSdk 36+ | 36 | [Play 도움말](https://support.google.com/googleplay/android-developer/answer/11926878) |

추적 패키지(Play 스토어 리스팅에서 확인): 유튜브 `com.google.android.youtube`, 인스타그램 `com.instagram.android`·라이트 `com.instagram.lite`, 틱톡 `com.ss.android.ugc.trill`(한국 스토어 표시본)·`com.zhiliaoapp.musically`·`com.zhiliaoapp.musically.go`·`com.ss.android.ugc.tiktok.lite`.

확인하지 못한 것: `UsageEventsQuery.Builder.setPackageNames`가 생긴 API 레벨(35 또는 36), 사용 기록 이벤트 지연의 공식 수치, Data safety 정확한 항목 이름, 알림 쿨다운·강제 그룹핑(언론 보도만). → 코드에서 SDK 분기·재조회·그룹 키로 대비했다.

## 5. AI 기술 (모델·SDK·기기 안 모델)
| 주제 | 확인한 사실 | 우리 결정 | 출처 |
|---|---|---|---|
| SDK | `com.google.firebase:firebase-ai`(BoM 35.1.0 → 18.1.0), `GenerativeBackend.googleAI()`. 18.0.0에서 Gemini 3.x의 temperature·topK·topP 지원 중단 | 생성 설정은 JSON 스키마만 | [시작하기](https://firebase.google.com/docs/ai-logic/get-started?platform=android) |
| 모델 | `gemini-3.5-flash-lite` 안정판(2026-07-21 출시, 종료 2027-07-21 이후), `gemini-3.8-flash` 안정판(고품질). 2.0은 2026-06-01 종료, 2.5는 신규 프로젝트 불가, 3.1-flash-lite는 2027-05-07 종료 | 기본 3.5-flash-lite, Remote Config로 교체 | [모델](https://firebase.google.com/docs/ai-logic/models), [지원 중단](https://ai.google.dev/gemini-api/docs/deprecations) |
| 가격(유료, 100만 토큰) | 3.5-flash-lite 입력 $0.30 / 출력 $2.50, 3.8-flash $0.75 / $3.75(2026-12-31까지). 무료 등급 있음 | 출시 전 가격표 재확인 | [가격](https://aistudio.google.com/docs/pricing) (요약 도구 경유, 재확인 필요) |
| 구조화 출력·함수 호출 | `responseMimeType = "application/json"` + `responseSchema`. 함수 선언 최대 128개, 앱이 직접 실행하고 결과를 돌려줌 | 리포트·문구 팩은 스키마, 코치는 함수 호출 루프 | [구조화 출력](https://firebase.google.com/docs/ai-logic/generate-structured-output?platform=android), [함수 호출](https://firebase.google.com/docs/ai-logic/function-calling?platform=android) |
| App Check | **2026-11-02부터 Firebase AI Logic은 App Check 적용 필수**(최대 2027-01-21까지 연기 가능). 제한 사용 토큰 지원 | Play Integrity + 디버그 공급자, `useLimitedUseAppCheckTokens = true` | [AI Logic App Check](https://firebase.google.com/docs/ai-logic/app-check) |
| 무료 등급 | 결제 계정 없이(Spark) Gemini Developer API 무료 등급 사용 가능. 사용자당 기본 100 RPM. 모델별 무료 한도는 문서에 없음(AI Studio에서 확인) | 사용자당 하루 상한(05 문서 2절) | [가격 정책](https://firebase.google.com/docs/ai-logic/pricing), [할당량](https://firebase.google.com/docs/ai-logic/quotas) |
| 데이터 사용 | 무료 등급: 입력·출력을 Google이 개선에 쓰고 사람 검토자가 볼 수 있음, "민감·기밀·개인 정보를 보내지 말 것". 유료 등급: 개선에 쓰지 않음 | 집계만 보냄, 베타 이후 유료 등급 | [Gemini API 약관](https://ai.google.dev/gemini-api/terms) |
| 연령 | **"만 18세 이상이어야 API를 쓸 수 있다"**, 18세 미만 대상·이용 가능성이 높은 서비스 사용 금지 | 앱 전체 18세 이상(ADR-0009) | 같은 곳 |
| 기기 안 모델 | ML Kit GenAI Prompt API `genai-prompt` 1.0.0-beta5(베타). 앱이 맨 앞에 있을 때만(포그라운드 서비스에서는 `BACKGROUND_USE_BLOCKED`), 앱별 할당량. 지원: 픽셀 9~11, 갤럭시 S26·Z 폴드7/8 등. 한국어 지원 목록 없음 | 짧은 작업에만, 평가로 확인. 개입 순간 문구는 미리 만든 문구 팩 | [ML Kit GenAI](https://developers.google.com/ml-kit/genai), [릴리스 노트](https://developers.google.com/ml-kit/release-notes) |
| 법 | 개인정보 보호법(법률 제21445호, 2026-09-11 시행). 만 14세 미만 법정대리인 동의(제22조의2), 국외 이전 고지(제28조의8) | 18세 이상 + 처리방침에 국외 이전 | [법령](https://www.law.go.kr/LSW/lsInfoP.do?lsId=011357&ancYnChk=0), [개인정보위](https://pipc.go.kr/np/default/page.do?mCode=D060040000) |

## 6. 라이브러리 버전
`docs/03_ARCHITECTURE.md` 6절 표와 ADR-0007이 결론이다. 근거(릴리스 노트·Maven Central 링크)와 확인하지 못한 항목은 `research/versions.md`.
- 꼭 알아 둘 변화: **AGP 9는 Kotlin 내장**(kotlin-android 플러그인 금지, kapt 불가), **KSP 2.3.x는 Kotlin과 따로 번호**, **Navigation Compose는 유지보수 모드 → Navigation 3**, **Room 3 안정판이 나왔지만 Room 2.8 유지**, **Firebase BoM 35부터 minSdk 24**, **Robolectric은 SDK 36+에서 JDK 21**.
- Play 목표 API: 2026-08-31부터 신규 앱·업데이트는 **targetSdk 36 이상**([Play Console 도움말](https://support.google.com/googleplay/android-developer/answer/11926878)).

## 7. 이름 점검 — 사르르
- 웹에 색인된 앱스토어·Google Play 결과에서 '사르르'·'Sareureu' 앱을 찾지 못했다. Google Play 검색 페이지는 열리지 않아 **완전한 확인은 아니다**.
- '사르르'는 녹는 모양을 나타내는 일반 의태어라 **식품·디저트 이름에 흔하다**(쥬씨 '사르르 타르트', '사르르 버터쿠키', 같은 이름의 카페들). 앱 분야와 겹치지는 않지만 상표 식별력이 약할 수 있다.
- KIPRIS는 직접 조회하지 못했다. 출시·공모전 전에 **9류(앱), 42류(SaaS), 44류(건강), 41류(교육), 35류**로 '사르르', 'SAREUREU', '르르'를 검색한다. 르르 그림과 결합한 표장을 검토한다. 결과는 `docs/brand/BRAND.md` 5절 표에 적는다.
- 이 내용은 법률 자문이 아니다.

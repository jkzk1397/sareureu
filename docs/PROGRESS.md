# 진행 상황

> 규칙: 자기 줄의 체크박스만 바꾼다. 끝낸 항목 뒤에 PR 번호를 붙인다. 막힌 항목은 `⚠ #이슈`를 붙인다.
> 원샷 프롬프트를 실행한 에이전트는 단계를 끝낼 때마다 이 파일을 갱신하고 커밋한다.

## 0단계 · 저장소 기반 (R1)
- [x] Gradle wrapper, `gradle/libs.versions.toml`(03 문서의 버전 표 그대로), `build-logic` 컨벤션 플러그인
- [x] 20개 모듈 생성(키트의 모듈별 `AGENTS.md` 위치 그대로)
- [x] Spotless + ktlint, `.editorconfig`, CI(`.github/workflows/android-ci.yml`) 통과 (로컬 통과, 원격 CI는 PR 후 확인)
- [x] 앱 실행: 하단 탭 5개(오늘·리포트·코치·친구·설정) 빈 화면, 시연 모드 스위치

## 1단계 · 모델·디자인 시스템·시연 데이터
- [x] `core:model` 타입 전부(04 문서 2절) (R1)
- [x] `core:designsystem`: 라이트·다크 토큰, Pretendard, 컴포넌트, **르르 `BuddyAvatar`**(몸 그림 + 코드 얼굴 6표정 + 녹는 단계, 06 문서 8절), **하루 시계 `DayClock`·`WeekClock`**(06 문서 7절, 위가 0시) (R5)
- [x] 앱 아이콘(적응형, 라임 바탕 + 르르), `app_name`=사르르·`buddy_name`=르르 (R5)
- [x] `DemoScenario`: 14일 × 3패턴, 고정 시드, 가상 친구 3명, 가상 코호트 (R2)
- [x] Room 엔티티·DAO·스키마 내보내기, 저장소 인터페이스 (R2)
- [x] 오늘 탭: 목업 01과 같은 구성, 시연 데이터로 표시 (R5)

## 2단계 · 감지와 계산
- [ ] `UsageStatsSource` + 포그라운드 서비스 폴링(화면 켜짐일 때만) (R2/R3)
- [ ] `SessionTracker`: 숏폼끼리 60초 이내 전환은 한 세션, 화면 꺼짐 종료 (R2)
- [ ] 진입·이탈 계산(잠금 해제·홈·앱 종류·도미노·화면 끔) + 단위 테스트 (R2)
- [ ] `ContextClassifier` 8개 맥락 + 확신도 + 경계값 테스트 (R2)
- [ ] `core:insights`: 집계, 시계용 시간별·맥락별 분(`ClockBins`), 기준선, 감소율, 기록률, 위험 시간대, 유형, 녹는 단계(`MeltStageCalculator`) + 테스트 (R4)
- [ ] 온보딩: 연령 확인(만 18세 미만 종료)·프로필·하루 리듬·목표·말투·추적 앱·권한 고지·AI 동의(국외 이전 고지) (R3)

## 3단계 · 실시간 개입
- [ ] `NudgeEngine` 상태 기계(1~4단계, 연장, 오늘은 그만, 하루 3회 초과 시 2단계 시작) + 테스트 (R3)
- [ ] 오버레이 창 관리(Compose 수명 주기 연결), 플로팅 르르(가장자리에서 빼꼼, 녹는 단계, 끌기·가장자리 붙기) (R3)
- [ ] 카드 1~4단계 UI(목업 02·03·04) (R3/R5)
- [ ] 르르 메시지 알림(MessagingStyle + `BuddyBitmap` 아이콘 + 실루엣 작은 아이콘, 채널), 탭하면 코치 채팅 (R3)
- [ ] 예약 알림·위험 시간대 미리 알림(WorkManager) (R3)
- [ ] 기본 문구 팩 `assets/copy/default_pack.json` (R4)

## 4단계 · AI
- [ ] `core:ai-engine`: `LlmClient`, `MockLlmClient`, `PromptRegistry` + 프롬프트 v1(weekly_report, copy_pack, context_check, coach_chat, poke_draft), 스키마, 검증기, `SafetyFilter`, `BannedWords`, `CoachEngine` + 단위 테스트 (R4)
- [ ] `core:ai`: `CloudLlmClient`(Firebase, 설정 파일 없으면 비활성), `OnDeviceLlmClient`(스텁 가능), `AiRouter`, 생성기들 (R4)
- [ ] 주간 리포트 화면(목업 06) + 가설 판정 저장 + 전략 선택 + 임계값 제안 (R4/R5)
- [ ] 코치 채팅(도구 호출 루프, 근거 칩, 계획 저장 버튼)(목업 07) (R4)
- [ ] `AiCallLog` + 설정의 "AI가 한 일" + AI 문장 '신고' (R4)
- [ ] `:core:ai-engine:aiEval` 하네스 + 평가 데이터 + 보고서 (R4)

## 5단계 · 리포트·친구·설정
- [ ] 리포트 차트: 일주일 시계(+표 보기 히트맵), 맥락별 막대, 경로 흐름도, 세션 길이, 또래 비교(목업 05) (R5)
- [ ] 친구(가상 친구): 감소율 랭킹, 잔소리 맡기기(AI 초안), 함께 챌린지(목업 08) (R6)
- [ ] (P1) Firebase 연동: 익명 로그인, 친구 연결, publicDaily, 잔소리 받은편지함 (R6)
- [ ] 설정: 맥락별 임계값, 예약 알림, 추적 앱, 감지 방식, AI 제공자, 개인정보(내보내기·삭제), 테마, 시연 (R1)

## 6단계 · 마감
- [ ] 접근성(TalkBack 라벨, 글자 200%, 터치 48dp), 다크 모드 전 화면 점검 (R5)
- [ ] 배터리: 화면 꺼짐 시 폴링 중지 확인 (R3)
- [ ] `README.md`, `docs/BUILD_REPORT.md`, 시연 스크립트 리허설 (R1)
- [ ] Play Console 제출 문안: 포그라운드 서비스(specialUse) 설명·동영상, (정밀 모드를 넣었다면) 접근성 선언·동영상, Data safety, 개인정보처리방침(국외 이전 포함) (R1)

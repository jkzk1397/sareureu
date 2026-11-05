# :feature:today — 모듈 규칙
- 담당: R5 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 오늘 탭: 하루 시계, 어제 대비, 보는 중 카드, 캐릭터 한마디, 받은 잔소리, 내 계획, 위험 시간대 카드
- 공개 API: 경로 키 `TodayKey`(`@Serializable`, `NavKey`, `navigation/` 패키지) + Navigation 3 entry provider DSL에 붙는 확장 함수 `todayEntries()` 하나
- 의존 가능: designsystem, data
- 금지: 계산하지 않는다. `core:data` 저장소가 주는 값(insights가 계산한 것)을 그리기만. 시연 버튼은 `DemoController`(core:data)로만
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.

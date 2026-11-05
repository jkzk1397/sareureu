# :feature:friends — 모듈 규칙
- 담당: R6 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 친구 목록·초대, 감소율 랭킹, 주간 리그(P1), 잔소리 맡기기(AI 초안), 함께 챌린지
- 공개 API: 경로 키 `FriendsKey`(`@Serializable`, `NavKey`, `navigation/` 패키지) + Navigation 3 entry provider DSL에 붙는 확장 함수 `friendsEntries()` 하나
- 의존 가능: designsystem, data, social, ai
- 금지: 꼴찌 공개 금지(상위 3 + 나). 차단·신고 버튼
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.

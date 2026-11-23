# :feature:friends — 모듈 규칙
- 담당: R6 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 친구 목록(시청 중·연속 일수)·QR 초대, 목표 지킴 연속 일수 랭킹, 주간 리그(P1), 시청 중인 친구에게 잔소리 보내기(AI 초안), 함께 챌린지
- 공개 API: 경로 키 `FriendsKey`(`@Serializable`, `NavKey`, `navigation/` 패키지) + Navigation 3 entry provider DSL에 붙는 확장 함수 `friendsEntries()` 하나
- 의존 가능: designsystem, data, social, ai
- 금지: 친구 랭킹은 연속 일수만(시청 시간 표시 금지, ADR-0012). 차단·신고 버튼 유지
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.

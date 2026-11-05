# :core:social — 모듈 규칙
- 담당: R6 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 친구 관계, 잔소리 받은편지함, 친구 랭킹·리그, Firebase(Auth·Firestore), 오프라인 가상 친구
- 공개 API: `FriendRepository`, `PokeInbox`, `LeagueRepository`(03 문서 4절과 같음)
- 의존 가능: data, insights, model, common
- 금지: 친구에게는 감소율·기록률만 공유. 시청 시간·맥락·앱은 공유 금지
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.

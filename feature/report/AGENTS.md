# :feature:report — 모듈 규칙
- 담당: R5 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 리포트 탭: 일주일 시계(+표), 맥락별 합계, 경로 흐름도, 세션 길이, 또래 비교, AI 주간 분석(가설·근거·전략·임계값 제안), 유형 카드
- 공개 API: 경로 키 `ReportKey`(`@Serializable`, `NavKey`, `navigation/` 패키지) + Navigation 3 entry provider DSL에 붙는 확장 함수 `reportEntries()` 하나
- 의존 가능: designsystem, data, ai
- 금지: AI 문장은 검증 통과본만, 관찰(코드)과 가설(AI)을 화면에서 분리
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.

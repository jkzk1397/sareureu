# :core:designsystem — 모듈 규칙
- 담당: R5 (실제 사람은 .github/CODEOWNERS)
- 하는 일: PauseTheme(라이트·다크), 토큰, Pretendard, 컴포넌트, 차트(DayClock, WeekClock, Heatmap, FlowDiagram 등), 르르(`BuddyAvatar`·`BuddyFaceGeometry`·`BuddyBitmap`, 06 문서 8절)
- 공개 API: `PauseTheme`, `PauseColors`, 컴포넌트들, `BuddyAvatar(expression, size, melt)`, `Wordmark`
- 의존 가능: model, common
- 금지: 화면 로직 금지. 토큰 변경은 R5만. 르르 표정별 그림 파일 추가 금지(얼굴은 코드). `res/drawable-nodpi/designsystem_buddy_*.webp`는 R5만 교체
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.

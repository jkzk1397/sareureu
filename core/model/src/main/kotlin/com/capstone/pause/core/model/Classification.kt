package com.capstone.pause.core.model

/** 맥락(시간대 범주). 이 순서가 색·쌓는 순서다(06 문서 2.2절). */
enum class ContextSlot { WAKE_UP, COMMUTE, CLASS_WORK, MEAL, BREAK, PRE_SLEEP, LATE_NIGHT, OTHER }

/** 확신도. */
enum class Confidence { HIGH, MEDIUM, LOW }

/** 앱 종류. */
enum class AppKind { SHORTFORM, MESSENGER, SOCIAL, VIDEO, STUDY_WORK, BROWSER, GAME, SHOPPING, LAUNCHER, OTHER }

/** 숏폼 화면 종류. APP_UNKNOWN = 앱 단위 모드. */
enum class Surface { SHORTS, REELS, TIKTOK_FEED, APP_UNKNOWN }

/** 데이터 출처. 시연 데이터는 실제 데이터와 섞지 않는다. */
enum class DataSource { REAL, DEMO }

/** 한 탭 체크인(어땠어?). */
enum class Mood { GOOD, MEH, BAD }

/** 추적하는 앱. */
data class TrackedApp(val packageName: String, val label: String, val kind: AppKind, val enabled: Boolean)

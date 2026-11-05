package com.capstone.pause.core.model

/** 르르 표정(06 문서 8절). 표정은 상황으로만 정하고 녹은 정도로 바꾸지 않는다. */
enum class BuddyExpression { NEUTRAL, WATCHING, CALLING, WORRIED, CHEER, SLEEPY }

/** 르르가 녹은 정도 0~3. 오늘 숏폼 ÷ 기준으로 정한다. */
enum class MeltStage { NONE, SOFT, PUDDLE, MELTED }

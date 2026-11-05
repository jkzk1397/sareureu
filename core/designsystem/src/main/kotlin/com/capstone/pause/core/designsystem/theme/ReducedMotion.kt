package com.capstone.pause.core.designsystem.theme

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 시스템 '애니메이션 제거'(배율 0)인지. 순수 판정(06 문서 4절). */
object ReducedMotion {
    /** 애니메이터 배율과 전환 배율 중 하나라도 0이면 움직임을 끈다. */
    fun isReduced(animatorScale: Float, transitionScale: Float): Boolean = animatorScale == 0f || transitionScale == 0f

    /** 시스템 설정에서 읽는다. 읽지 못하면 움직임을 켠 것으로 본다. */
    fun read(resolver: ContentResolver): Boolean = isReduced(
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
        Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f),
    )
}

/** '애니메이션 제거'가 켜져 있으면 true. 화면이 처음 그려질 때 한 번 읽는다. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { ReducedMotion.read(resolver) }
}

/** 움직임을 끈 경우 바로 바뀌는 [snap], 아니면 [spec]. */
@Composable
fun <T> motionSpec(spec: AnimationSpec<T>): AnimationSpec<T> = if (rememberReducedMotion()) snap() else spec

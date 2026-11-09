package com.capstone.pause.core.ai.client

import com.capstone.pause.core.ai.BuildConfig
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class DebugSecretStoreTest {
    @Test
    fun prefsNameMatchesSdkTemplate() {
        assertEquals("com.google.firebase.appcheck.debug.store.abc", DebugSecretStore.prefsName("abc"))
    }

    @Test
    fun emptySecretMeansDoNothing() {
        assertNull(DebugSecretStore.secretOrNull(""))
        assertNull(DebugSecretStore.secretOrNull("  "))
        assertEquals("1234", DebugSecretStore.secretOrNull(" 1234 "))
    }

    @Test
    fun buildConfigSecretIsEmptyOrUuid() {
        // CI·팀원 PC(local.properties에 값 없음)는 빈 문자열이라 예전과 똑같이 동작한다.
        val s = BuildConfig.APP_CHECK_DEBUG_SECRET
        assertTrue(
            s.isEmpty() || s.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")),
            "모양: ${s.length}자",
        )
    }
}

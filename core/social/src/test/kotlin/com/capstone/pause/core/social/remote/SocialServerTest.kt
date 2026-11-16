package com.capstone.pause.core.social.remote

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SocialServerTest {
    @Test
    fun blankOrPlainHttpMeansDemoFriends() {
        assertFalse(SocialServer("").enabled)
        assertFalse(SocialServer("http://example.workers.dev").enabled, "토큰을 보내므로 https만")
        assertTrue(SocialServer("https://example.workers.dev").enabled)
    }

    @Test
    fun joinsPathsWithOneSlash() {
        assertEquals("https://x.dev/api/v1/sharing", SocialServer("https://x.dev/").url("/api/v1/sharing"))
        assertEquals("https://x.dev/health", SocialServer("https://x.dev").url("health"))
    }
}

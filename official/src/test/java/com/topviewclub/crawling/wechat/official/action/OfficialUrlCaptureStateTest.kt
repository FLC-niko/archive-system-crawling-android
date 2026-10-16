package com.topviewclub.crawling.wechat.official.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialUrlCaptureStateTest {
    @Test
    fun `activity result is consumed once by its own task`() {
        val state = OfficialUrlCaptureState()

        assertTrue(state.begin(41L))
        assertTrue(state.complete(41L, OfficialUrlCaptureResult(validUrl = true)))
        assertEquals(OfficialUrlCaptureResult(validUrl = true), state.takeCompleted(41L))
        assertNull(state.takeCompleted(41L))
    }

    @Test
    fun `late activity from old task cannot complete the new task`() {
        val state = OfficialUrlCaptureState()

        assertTrue(state.begin(41L))
        assertTrue(state.begin(42L))
        assertFalse(state.complete(41L, OfficialUrlCaptureResult(validUrl = true)))
        assertNull(state.takeCompleted(42L))
        assertTrue(state.isActive(42L))
    }

    @Test
    fun `timeout result cannot be replaced by a late clipboard callback`() {
        val state = OfficialUrlCaptureState()

        assertTrue(state.begin(41L))
        assertTrue(state.complete(41L, OfficialUrlCaptureResult(validUrl = false, timedOut = true)))
        assertFalse(state.complete(41L, OfficialUrlCaptureResult(validUrl = true)))
        assertEquals(
            OfficialUrlCaptureResult(validUrl = false, timedOut = true),
            state.takeCompleted(41L),
        )
    }

    @Test
    fun `only WeChat article URLs are accepted`() {
        assertTrue(isValidOfficialArticleUrl("https://mp.weixin.qq.com/s/example"))
        assertTrue(isValidOfficialArticleUrl("http://mp.weixin.qq.com/s/example"))
        assertFalse(isValidOfficialArticleUrl(""))
        assertFalse(isValidOfficialArticleUrl("https://example.com/article"))
    }
}

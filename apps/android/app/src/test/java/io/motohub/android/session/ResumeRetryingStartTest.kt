// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Vincenzo Buonomano and the MOTO-HUB contributors.
// Part of MOTO-HUB. Free software under the GNU AGPL v3; see LICENSE.
package io.motohub.android.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRetryingStartTest {
    private class FakeStarter(vararg outcomes: Boolean) {
        private val queue = ArrayDeque(outcomes.toList())
        val calls = mutableListOf<String>()
        fun start(value: String): Boolean {
            calls += value
            return queue.removeFirst()
        }
    }

    @Test
    fun anAcceptedStartHoldsNothing() {
        val starter = FakeStarter(true)
        val retrying = ResumeRetryingStart(starter::start)
        assertTrue(retrying.request("consent"))
        assertNull(retrying.onResume())
        assertEquals(listOf("consent"), starter.calls)
    }

    @Test
    fun aRefusedStartIsRetriedOnceOnResume() {
        // WB-27: refused before the activity is resumed, accepted once it is.
        val starter = FakeStarter(false, true)
        val retrying = ResumeRetryingStart(starter::start)
        assertFalse(retrying.request("consent"))
        assertEquals(true, retrying.onResume())
        assertNull(retrying.onResume())
        assertEquals(listOf("consent", "consent"), starter.calls)
    }

    @Test
    fun aSecondRefusalGivesUp() {
        val starter = FakeStarter(false, false)
        val retrying = ResumeRetryingStart(starter::start)
        assertFalse(retrying.request("consent"))
        assertEquals(false, retrying.onResume())
        // Never a third try: the caller has turned the second refusal into a cancellation.
        assertNull(retrying.onResume())
        assertEquals(2, starter.calls.size)
    }

    @Test
    fun resumesWithoutARefusalDoNothing() {
        val starter = FakeStarter()
        val retrying = ResumeRetryingStart(starter::start)
        assertNull(retrying.onResume())
        assertTrue(starter.calls.isEmpty())
    }

    @Test
    fun aDiscardedStartIsNeverRetried() {
        // WB-28: the link dropped while the app was away, so the held autostart is dropped too.
        val starter = FakeStarter(false)
        val retrying = ResumeRetryingStart(starter::start)
        assertFalse(retrying.request("autostart"))
        assertTrue(retrying.discard())
        assertNull(retrying.onResume())
        assertFalse(retrying.discard())
        assertEquals(listOf("autostart"), starter.calls)
    }

    @Test
    fun aNewConsentReplacesAHeldOne() {
        val starter = FakeStarter(false, true)
        val retrying = ResumeRetryingStart(starter::start)
        assertFalse(retrying.request("old"))
        assertTrue(retrying.request("new"))
        assertNull(retrying.onResume())
        assertEquals(listOf("old", "new"), starter.calls)
    }
}
